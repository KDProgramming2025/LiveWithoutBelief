/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Article metadata persisted locally.
 *
 * Immutable identifying & descriptive fields of an article.
 * Media assets in [ArticleAssetEntity]. Fields [label], [order],
 * [coverUrl], [iconUrl] and [indexUrl] originate from the remote manifest enabling offline list
 * rendering and direct WebView fast-path loading when a pre-rendered HTML export exists.
 *
 * @property id Stable unique article identifier.
 * @property title Human readable title.
 * @property slug URL-safe slug for deep links.
 * @property version Monotonically increasing version for cache invalidation.
 * @property updatedAt ISO-8601 last modification timestamp.
 * @property wordCount Approximate word count for reading time heuristics.
 * @property label Optional category / badge label.
 * @property order Stable ascending ordering index for deterministic UI ordering.
 * @property coverUrl Cover image URL (mandatory, server guaranteed).
 * @property iconUrl Icon image URL (mandatory, server guaranteed).
 * @property indexUrl Pre-rendered static HTML index URL for direct WebView loading (mandatory).
 */
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
    // Optional menu/category label (null if uncategorized)
    val label: String?,
    // Stable ordering integer (ascending) used for deterministic UI ordering
    val `order`: Int,
    // Cover image URL provided by server (non-null)
    val coverUrl: String,
    // Icon image URL provided by server (non-null)
    val iconUrl: String,
    // Pre-rendered index URL for fast reader loading (mandatory in current architecture)
    val indexUrl: String,
)

// ArticleContentEntity removed: no local HTML/plain text storage.

/**
 * Media / auxiliary assets belonging to an article.
 *
 * Each asset can be lazily fetched or cached (image, audio, video, etc.)
 *
 * @property id Unique asset id (may be derived from URL or server issued)
 * @property articleId FK to [ArticleEntity.id]
 * @property type Media type/category (image, audio, video, etc.)
 * @property uri Local or remote URI string
 * @property checksum Optional checksum for integrity validation
 * @property width Original pixel width (nullable if not applicable)
 * @property height Original pixel height (nullable if not applicable)
 * @property sizeBytes Approximate size for storage/statistics (nullable if unknown)
 */
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

/**
 * User-defined bookmark folder grouping.
 *
 * Constraint ensures folder names are unique per user.
 *
 * @property id Unique folder id
 * @property userId Owner user id
 * @property name Human readable folder name (unique per user)
 * @property createdAt ISO-8601 creation timestamp
 */
@Entity(tableName = "bookmark_folders", indices = [Index(value = ["userId", "name"], unique = true)])
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val createdAt: String,
)

/**
 * Single bookmark referencing an article optionally scoped to a folder.
 *
 * Unique composite index enforces only one bookmark of an article per user.
 *
 * @property id Unique bookmark id
 * @property userId Owner user id
 * @property articleId Target article id
 * @property folderId Optional containing folder
 * @property createdAt ISO-8601 creation timestamp
 */
@Entity(tableName = "bookmarks", indices = [Index(value = ["userId", "articleId"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val articleId: String,
    val folderId: String?,
    val createdAt: String,
)

/**
 * Text range annotation created by a user.
 *
 * Offsets are character offsets within the article's plain text representation.
 *
 * @property id Unique annotation id
 * @property userId Owner user id
 * @property articleId Associated article
 * @property startOffset Inclusive character start offset
 * @property endOffset Exclusive character end offset
 * @property anchorHash Stable hash of the anchored text (re-validate after content updates)
 * @property createdAt ISO-8601 creation timestamp
 */
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

/**
 * Discussion thread message attached to an annotation.
 *
 * Allows asynchronous Q/A or commentary per annotation for the owning user.
 *
 * @property id Unique message id
 * @property annotationId Parent annotation id
 * @property userId Message author user id
 * @property type Message content type (text, image ref, etc.)
 * @property contentRef Reference to actual content (text blob key, asset id, etc.)
 * @property createdAt ISO-8601 creation timestamp
 */
@Entity(tableName = "thread_messages", indices = [Index(value = ["annotationId"])])
data class ThreadMessageEntity(
    @PrimaryKey val id: String,
    val annotationId: String,
    val userId: String,
    val type: String,
    val contentRef: String,
    val createdAt: String,
)

/** Offline-cached navigation menu item (table fully replaced on each successful sync). */
@Entity(tableName = "menu_items")
data class MenuItemEntity(
    /** Stable backend identifier */
    @PrimaryKey val id: String,
    /** Display title */
    val title: String,
    /** Optional label/category tag */
    val label: String?,
    /** Stable ordering integer (ascending) */
    val `order`: Int,
    /** Optional icon URL or relative path */
    val iconPath: String?,
    /** ISO-8601 creation timestamp */
    val createdAt: String,
)

/** DAO for persisted menu items enabling offline availability. */
@Dao
interface MenuDao {
    /** Stream ordered menu items; emits on any table change. */
    @Query("SELECT * FROM menu_items ORDER BY `order` ASC, title ASC")
    fun observeMenu(): Flow<List<MenuItemEntity>>

    /** Replace all rows with [items] in a single transaction. */
    @Query("DELETE FROM menu_items")
    suspend fun clearAll()

    /** Insert or replace all [items] after clearing existing data. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MenuItemEntity>)
}

/**
 * Main Room database entry point bundling all persistent entities.
 *
 * Version history intentionally reset (pre-release, schema not stable). Single version = 1.
 */
@Database(
    entities = [
        ArticleEntity::class,
        ArticleAssetEntity::class,
        BookmarkFolderEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        ThreadMessageEntity::class,
        MenuItemEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    /** Data access object for article metadata & queries */
    abstract fun articleDao(): ArticleDao

    /** DAO for user bookmarks */
    abstract fun bookmarkDao(): BookmarkDao

    /** DAO for bookmark folders */
    abstract fun folderDao(): FolderDao

    /** DAO for text range annotations */
    abstract fun annotationDao(): AnnotationDao

    /** DAO for annotation thread messages */
    abstract fun threadMessageDao(): ThreadMessageDao

    /** DAO for navigation menu items */
    abstract fun menuDao(): MenuDao
}
