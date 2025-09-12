/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Article(
    val id: String,
    val title: String,
    val slug: String,
    val version: Int,
    val updatedAt: String,
    val wordCount: Int,
)

@Serializable
data class ArticleContent(
    val articleId: String,
    val htmlBody: String,
    val plainText: String,
    val textHash: String,
)

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

@Serializable
data class Bookmark(
    val id: String,
    val articleId: String,
    val folderId: String?,
    val createdAt: String,
)

@Serializable
data class BookmarkFolder(
    val id: String,
    val name: String,
    val createdAt: String,
)

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val createdAt: String,
)

@Serializable
data class Annotation(
    val id: String,
    val articleId: String,
    val startOffset: Int,
    val endOffset: Int,
    val anchorHash: String,
    val createdAt: String,
)

@Serializable
data class ThreadMessage(
    val id: String,
    val annotationId: String,
    // text | image | audio | pdf | system
    val type: String,
    val contentRef: String,
    val createdAt: String,
)

@Serializable
data class ReadingProgress(
    val articleId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val progress: Double,
    val updatedAt: String,
)
