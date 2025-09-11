/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
)

@Entity(tableName = "article_contents")
data class ArticleContentEntity(
    @PrimaryKey val articleId: String,
    val htmlBody: String,
    val plainText: String,
    val textHash: String,
)

@Entity(tableName = "article_assets")
data class ArticleAssetEntity(
    @PrimaryKey val id: String,
    val articleId: String,
    val type: String,
    val uri: String,
    val checksum: String,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
)

@Entity(tableName = "bookmark_folders", indices = [Index(value = ["userId", "name"], unique = true)])
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val createdAt: String,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["userId", "articleId"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val articleId: String,
    val folderId: String?,
    val createdAt: String,
)

@Entity(tableName = "annotations", indices = [Index(value = ["userId", "articleId"])])
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val articleId: String,
    val startOffset: Int,
    val endOffset: Int,
    val anchorHash: String,
    val createdAt: String,
)

@Entity(tableName = "thread_messages", indices = [Index(value = ["annotationId"])])
data class ThreadMessageEntity(
    @PrimaryKey val id: String,
    val annotationId: String,
    val userId: String,
    val type: String,
    val contentRef: String,
    val createdAt: String,
)

// Reading progress for pagination / resume (one row per article)
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val articleId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val progress: Double, // 0.0 - 1.0
    val updatedAt: String,
)

@Database(
    entities = [
        ArticleEntity::class,
        ArticleContentEntity::class,
        ArticleAssetEntity::class,
        BookmarkFolderEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        ThreadMessageEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun folderDao(): FolderDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun threadMessageDao(): ThreadMessageDao
    abstract fun readingProgressDao(): ReadingProgressDao
}
