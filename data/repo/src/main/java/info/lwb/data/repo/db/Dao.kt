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
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    fun observeBookmarks(userId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM bookmark_folders WHERE userId = :userId")
    fun observeFolders(userId: String): Flow<List<BookmarkFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: BookmarkFolderEntity)

    @Delete
    suspend fun delete(folder: BookmarkFolderEntity)
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
