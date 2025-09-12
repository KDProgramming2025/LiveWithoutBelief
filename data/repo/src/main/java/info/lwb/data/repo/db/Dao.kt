/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticle(id: String): ArticleEntity?

    @Query("SELECT * FROM articles")
    suspend fun listArticles(): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(article: ArticleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: ArticleContentEntity)

    @Transaction
    suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) {
        upsertArticle(article)
        upsertContent(content)
    }

    @Query("SELECT * FROM article_contents WHERE articleId = :articleId")
    suspend fun getContent(articleId: String): ArticleContentEntity?

    @Query("SELECT * FROM article_contents WHERE articleId = :articleId")
    suspend fun getArticleContent(articleId: String): ArticleContentEntity?

    // ----- Assets -----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssets(assets: List<ArticleAssetEntity>)

    @Query("DELETE FROM article_assets WHERE articleId = :articleId AND id NOT IN (:keepIds)")
    suspend fun pruneAssetsForArticle(articleId: String, keepIds: List<String>)

    @Query("SELECT * FROM article_assets WHERE articleId = :articleId")
    suspend fun listAssets(articleId: String): List<ArticleAssetEntity>

    // ----- Eviction helpers -----
    @Query("DELETE FROM article_contents WHERE articleId NOT IN (:keepIds)")
    suspend fun deleteContentsNotIn(keepIds: List<String>)

    @Query("DELETE FROM article_assets WHERE articleId NOT IN (:keepIds)")
    suspend fun deleteAssetsNotIn(keepIds: List<String>)

    @Query("DELETE FROM article_contents")
    suspend fun clearAllContents()

    @Query("DELETE FROM article_assets")
    suspend fun clearAllAssets()

    // Lightweight local search across title and plain text body (no FTS; uses LIKE)
    @Query(
        "SELECT a.id AS id, a.title AS title, a.slug AS slug, a.version AS version, " +
            "a.updatedAt AS updatedAt, a.wordCount AS wordCount, c.plainText AS plainText " +
            "FROM articles a JOIN article_contents c ON c.articleId = a.id " +
            "WHERE (c.plainText LIKE '%' || :q || '%' OR a.title LIKE '%' || :q || '%') " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow>
}

// POJO for search results (Room maps query columns by name)
data class ArticleSearchRow(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
    val plainText: String,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    fun observeBookmarks(userId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query(
        "SELECT a.* " +
            "FROM articles a JOIN bookmarks b ON b.articleId = a.id " +
            "WHERE b.userId = :userId AND (a.title LIKE '%' || :q || '%') " +
            "ORDER BY a.updatedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchBookmarkedArticles(userId: String, q: String, limit: Int, offset: Int): List<ArticleEntity>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM bookmark_folders WHERE userId = :userId")
    fun observeFolders(userId: String): Flow<List<BookmarkFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: BookmarkFolderEntity)

    @Delete
    suspend fun delete(folder: BookmarkFolderEntity)

    @Query("SELECT * FROM bookmark_folders WHERE userId = :userId AND name = :name LIMIT 1")
    suspend fun findByName(userId: String, name: String): BookmarkFolderEntity?
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE userId = :userId AND articleId = :articleId")
    fun observeAnnotations(userId: String, articleId: String): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(annotation: AnnotationEntity)
}

@Dao
interface ThreadMessageDao {
    @Query("SELECT * FROM thread_messages WHERE annotationId = :annotationId ORDER BY createdAt ASC")
    fun observeMessages(annotationId: String): Flow<List<ThreadMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(message: ThreadMessageEntity)
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE articleId = :articleId")
    fun observe(articleId: String): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)
}
