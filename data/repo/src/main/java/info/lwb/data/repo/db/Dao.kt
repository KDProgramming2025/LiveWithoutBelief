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
import kotlinx.coroutines.flow.Flow

/**
 * Data access layer for article metadata, auxiliary media assets and localized search.
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

    /** Observe all article rows; emits on every table change. */
    @Query("SELECT * FROM articles")
    fun observeArticles(): Flow<List<ArticleEntity>>

    /** Insert or replace a single article metadata record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: ArticleEntity)

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

    /** Delete asset rows whose owning article id is not in the keep set. */
    @Query("DELETE FROM article_assets WHERE articleId NOT IN (:keepIds)")
    suspend fun deleteAssetsNotIn(keepIds: List<String>)

    /** Remove all article asset rows (cache reset). */
    @Query("DELETE FROM article_assets")
    suspend fun clearAllAssets()

    /** Delete article rows whose id is not in the keep set (authoritative pruning). */
    @Query("DELETE FROM articles WHERE id NOT IN (:keepIds)")
    suspend fun deleteArticlesNotIn(keepIds: List<String>)

    /** Remove all article rows (full reset). */
    @Query("DELETE FROM articles")
    suspend fun clearAllArticles()

    /** Title-only LIKE-based local search (body removed). */
    @Query(
        "SELECT a.id AS id, a.title AS title, a.slug AS slug, a.version AS version, " +
            "a.updatedAt AS updatedAt, a.wordCount AS wordCount, " +
            "a.label AS label, a.`order` AS ordering, a.coverUrl AS coverUrl, " +
            "a.iconUrl AS iconUrl, a.indexUrl AS indexUrl " +
            "FROM articles a " +
            "WHERE a.title LIKE '%' || :q || '%' " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow>
}

/**
 * Projection row returned by [ArticleDao.searchArticlesLike].
 * All properties map 1:1 with the selected columns in the query.
 */
data class ArticleSearchRow(
    /** Article primary identifier. */
    val id: String,
    /** Current localized display title. */
    val title: String,
    /** URL friendly slug used in links. */
    val slug: String,
    /** Monotonic content/version integer. */
    val version: Int,
    /** ISO-8601 last update timestamp. */
    val updatedAt: String,
    /** Estimated word count for reading time heuristics. */
    val wordCount: Int,
    /** Optional category/label badge. */
    val label: String?,
    /** Explicit ordering index (ascending). */
    val ordering: Int?,
    /** Cover image URL (nullable for legacy rows). */
    val coverUrl: String?,
    /** Icon/thumbnail image URL. */
    val iconUrl: String?,
    /** Pre-rendered index HTML URL. */
    val indexUrl: String?,
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
