/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.model

import kotlinx.serialization.Serializable

/**
 * Represents a published article available to the reader.
 * @property id Stable unique identifier.
 * @property title Human readable display title.
 * @property slug URL friendly slug used in links.
 * @property version Incrementing content version for cache busting.
 * @property updatedAt ISO-8601 last update timestamp.
 * @property wordCount Estimated word count for reading time heuristics.
 * @property coverUrl Optional large cover image URL.
 * @property iconUrl Optional small icon / thumbnail URL.
 */
@Serializable
data class Article(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
    val coverUrl: String? = null,
    val iconUrl: String? = null,
)

/**
 * Full article content payload loaded for reading.
 * @property articleId Related [Article] id.
 * @property htmlBody Raw HTML body.
 * @property plainText Extracted plain text (search / indexing).
 * @property textHash Hash of the text for change detection.
 * @property indexUrl Optional pre-rendered server URL to prefer over inline body.
 */
@Serializable
data class ArticleContent(
    val articleId: String,
    val htmlBody: String,
    val plainText: String,
    val textHash: String,
    val indexUrl: String? = null,
)

/**
 * Asset belonging to an article (image, video, etc.).
 * @property id Asset id.
 * @property articleId Parent article id.
 * @property type Media type (e.g. "image", "video").
 * @property uri Remote/local URI.
 * @property checksum Integrity checksum for validation.
 * @property width Optional width in pixels.
 * @property height Optional height in pixels.
 * @property sizeBytes Optional file size in bytes.
 */
@Serializable
data class ArticleAsset(
    val id: String,
    val articleId: String,
    // image | video
    val type: String,
    val uri: String,
    val checksum: String,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
)

/**
 * User bookmark pointing at an article.
 * @property id Bookmark id.
 * @property articleId Target article id.
 * @property folderId Optional folder grouping id.
 * @property createdAt ISO-8601 creation timestamp.
 */
@Serializable
data class Bookmark(val id: String, val articleId: String, val folderId: String?, val createdAt: String)

/**
 * Folder grouping user bookmarks.
 * @property id Folder id.
 * @property name Display name.
 * @property createdAt ISO-8601 creation timestamp.
 */
@Serializable
data class BookmarkFolder(val id: String, val name: String, val createdAt: String)

/**
 * Tag for classifying articles.
 * @property id Tag id.
 * @property name Tag label.
 * @property createdAt ISO-8601 creation timestamp.
 */
@Serializable
data class Tag(val id: String, val name: String, val createdAt: String)

/**
 * User annotation referencing a text span of an article.
 * @property id Annotation id.
 * @property articleId Article id.
 * @property startOffset Inclusive start character offset.
 * @property endOffset Exclusive end character offset.
 * @property anchorHash Hash derived from surrounding text for resilience.
 * @property createdAt ISO-8601 creation timestamp.
 */
@Serializable
data class Annotation(
    val id: String,
    val articleId: String,
    val startOffset: Int,
    val endOffset: Int,
    val anchorHash: String,
    val createdAt: String,
)

/**
 * Message inside a private thread attached to an annotation.
 * @property id Message id.
 * @property annotationId Parent annotation id.
 * @property type Message type (text, image, audio, pdf, system).
 * @property contentRef Reference to stored payload / value.
 * @property createdAt ISO-8601 creation timestamp.
 */
@Serializable
data class ThreadMessage(
    val id: String,
    val annotationId: String,
    // text | image | audio | pdf | system
    val type: String,
    val contentRef: String,
    val createdAt: String,
)

/**
 * Snapshot of reading progress for an article.
 * @property articleId Article id.
 * @property pageIndex Current page index (0-based).
 * @property totalPages Total pages recorded.
 * @property progress Fractional completion 0.0..1.0.
 * @property updatedAt ISO-8601 last update timestamp.
 */
@Serializable
data class ReadingProgress(
    val articleId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val progress: Double,
    val updatedAt: String,
)

/**
 * Menu entry used for navigation / home grid.
 * @property id Item id.
 * @property title Display title.
 * @property label Optional grouping label.
 * @property order Sort order (lower renders first).
 * @property iconPath Optional relative icon path.
 * @property createdAt ISO-8601 creation timestamp (may be blank for seed data).
 */
@Serializable
data class MenuItem(
    val id: String,
    val title: String,
    val label: String? = null,
    val order: Int = 0,
    val iconPath: String? = null,
    val createdAt: String = "",
)
