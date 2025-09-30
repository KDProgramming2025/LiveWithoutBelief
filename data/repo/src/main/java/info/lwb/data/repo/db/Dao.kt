/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data access layer for article metadata, content bodies, auxiliary media assets and
 * localized search. Centralized to keep multi-entity transactional operations simple.
 *
 * Rationale for keeping a single interface (detekt ComplexInterface):
 * 1. Methods operate on strongly related tables that are nearly always coordinated.
 * 2. Splitting right now would create additional indirection in repositories without
 *    reducing cognitive load (call sites would hold multiple DAO references).
 * 3. Transactional helper [upsertArticleWithContent] benefits from co-location.
 * 4. Future growth trigger: if more responsibilities than CRUD + search + assets
 *    appear, we will extract specialized DAOs (Content, Assets) and update modules.
 */
@Suppress("ComplexInterface")
@Dao
interface ArticleDao {
    /** Fetch a single article row by its stable identifier. */
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticle(id: String): ArticleEntity?

    /** List all article rows currently cached locally. */
    @Query("SELECT * FROM articles")
    suspend fun listArticles(): List<ArticleEntity>

    /** Insert or replace a single article metadata record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: ArticleEntity)

    /** Insert or replace a single article textual content body. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: ArticleContentEntity)

    /**
     * Atomically persist (replace) an article metadata row and its content body.
     * This prevents inconsistent states where one is updated without the other.
     */
    @Transaction
    suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) {
        upsertArticle(article)
        upsertContent(content)
    }

    /** Fetch only the content body for an article id. */
    @Query("SELECT * FROM article_contents WHERE articleId = :articleId")
    suspend fun getContent(articleId: String): ArticleContentEntity?

    /** Alias of [getContent]; retained for semantic clarity at call sites. */
    @Query("SELECT * FROM article_contents WHERE articleId = :articleId")
    suspend fun getArticleContent(articleId: String): ArticleContentEntity?

    // ----- Asset management -----

    /** Batch upsert of zero or more media asset references for an article. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssets(assets: List<ArticleAssetEntity>)

    /** Remove obsolete assets for an article keeping only the provided id set. */
    @Query("DELETE FROM article_assets WHERE articleId = :articleId AND id NOT IN (:keepIds)")
    suspend fun pruneAssetsForArticle(articleId: String, keepIds: List<String>)

    /** List all asset rows associated with the given article id. */
    @Query("SELECT * FROM article_assets WHERE articleId = :articleId")
    suspend fun listAssets(articleId: String): List<ArticleAssetEntity>

    // ----- Eviction helpers -----

    /** Delete content rows whose owning article id is not in the keep set. */
    @Query("DELETE FROM article_contents WHERE articleId NOT IN (:keepIds)")
    suspend fun deleteContentsNotIn(keepIds: List<String>)

    /** Delete asset rows whose owning article id is not in the keep set. */
    @Query("DELETE FROM article_assets WHERE articleId NOT IN (:keepIds)")
    suspend fun deleteAssetsNotIn(keepIds: List<String>)

    /** Remove all article content rows (cache reset). */
    @Query("DELETE FROM article_contents")
    suspend fun clearAllContents()

    /** Remove all article asset rows (cache reset). */
    @Query("DELETE FROM article_assets")
    suspend fun clearAllAssets()

    /**
     * Lightweight LIKE-based local search across title and plain text content.
     * Not using FTS yet due to limited scale; returns paged rows ordered by recency.
     */
    @Query(
        "SELECT a.id AS id, a.title AS title, a.slug AS slug, a.version AS version, " +
            "a.updatedAt AS updatedAt, a.wordCount AS wordCount, c.plainText AS plainText " +
            "FROM articles a JOIN article_contents c ON c.articleId = a.id " +
            "WHERE (c.plainText LIKE '%' || :q || '%' OR a.title LIKE '%' || :q || '%') " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow>
}

/**
 * Projection row returned by [ArticleDao.searchArticlesLike].
 * All properties map 1:1 with the selected columns in the query.
 */
data class ArticleSearchRow(
    /** Article primary id. */
    val id: String,
    /** Current localized title (may change across versions). */
    val title: String,
    /** Human readable slug used in shareable URLs. */
    val slug: String,
    /** Monotonic content version for optimistic updates. */
    val version: Int,
    /** RFC 3339 last update timestamp string. */
    val updatedAt: String,
    /** Approximate word count for reading time heuristics. */
    val wordCount: Int,
    /** Plain text body excerpt / full text used for LIKE search. */
    val plainText: String,
)

/** Data access for per-user bookmarks of articles. */
@Dao
interface BookmarkDao {
    /** Reactive stream of all bookmark rows for a user id. */
    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    fun observeBookmarks(userId: String): Flow<List<BookmarkEntity>>

    /** Insert or replace a bookmark. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    /** Delete a single bookmark row. */
    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    /** Paged LIKE search limited to bookmarked articles for the user. */
    @Query(
        "SELECT a.* " +
            "FROM articles a JOIN bookmarks b ON b.articleId = a.id " +
            "WHERE b.userId = :userId AND (a.title LIKE '%' || :q || '%') " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchBookmarkedArticles(userId: String, q: String, limit: Int, offset: Int): List<ArticleEntity>
}

/** Data access for user-defined bookmark folders. */
@Dao
interface FolderDao {
    /** Observe all folders belonging to a user. */
    @Query("SELECT * FROM bookmark_folders WHERE userId = :userId")
    fun observeFolders(userId: String): Flow<List<BookmarkFolderEntity>>

    /** Insert or update a folder metadata row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: BookmarkFolderEntity)

    /** Delete a specific folder (bookmarks may need cascading behavior configured). */
    @Delete
    suspend fun delete(folder: BookmarkFolderEntity)

    /** Find a folder by exact name for the given user or null if absent. */
    @Query("SELECT * FROM bookmark_folders WHERE userId = :userId AND name = :name LIMIT 1")
    suspend fun findByName(userId: String, name: String): BookmarkFolderEntity?
}

/** Data access for per-user inline text/image annotations linked to articles. */
@Dao
interface AnnotationDao {
    /** Observe all annotations authored by a user for a specific article id. */
    @Query("SELECT * FROM annotations WHERE userId = :userId AND articleId = :articleId")
    fun observeAnnotations(userId: String, articleId: String): Flow<List<AnnotationEntity>>

    /** Upsert (add or replace) an annotation row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(annotation: AnnotationEntity)
}

/** Data access for discussion thread messages associated with an annotation. */
@Dao
interface ThreadMessageDao {
    /** Observe all messages in ascending chronological order for an annotation. */
    @Query("SELECT * FROM thread_messages WHERE annotationId = :annotationId ORDER BY createdAt ASC")
    fun observeMessages(annotationId: String): Flow<List<ThreadMessageEntity>>

    /** Upsert (add or replace) a message row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(message: ThreadMessageEntity)
}

/** Data access for per-article reading progress state (e.g., scroll position). */
@Dao
interface ReadingProgressDao {
    /** Observe the reading progress entry for an article if present. */
    @Query("SELECT * FROM reading_progress WHERE articleId = :articleId")
    fun observe(articleId: String): Flow<ReadingProgressEntity?>

    /** Insert or replace a reading progress snapshot. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)
}
