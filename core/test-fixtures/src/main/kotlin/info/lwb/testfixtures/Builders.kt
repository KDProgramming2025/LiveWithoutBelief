/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import info.lwb.core.model.Article
import info.lwb.core.model.ArticleAsset
import info.lwb.core.model.ArticleContent

/**
 * Factory helpers for creating model objects in tests with sensible defaults.
 */
object Builders {
    /** Build an [Article] with overridable identifiers and version. */
    fun article(
        id: String = "a1",
        title: String = "Title",
        slug: String = "title",
        version: Int = 1,
        coverUrl: String = "https://example.com/covers/$id.jpg",
        iconUrl: String = "https://example.com/icons/$id.png",
        indexUrl: String = "https://example.com/$slug/index.html",
    ): Article = Article(
        id = id,
        title = title,
        slug = slug,
        version = version,
        updatedAt = "2025-01-01T00:00:00Z",
        wordCount = 1000,
        coverUrl = coverUrl,
        iconUrl = iconUrl,
        indexUrl = indexUrl,
    )

    /** Build [ArticleContent] for an article. */
    fun content(
        articleId: String = "a1",
        indexUrl: String? = "https://example.com/a1/index.html",
    ): ArticleContent = ArticleContent(
        articleId = articleId,
        htmlBody = "<p>Hello</p>",
        plainText = "Hello",
        textHash = "deadbeef",
        indexUrl = indexUrl,
    )

    /** Build an [ArticleAsset] (image by default). */
    fun asset(
        articleId: String = "a1",
        type: String = "image",
        uri: String = "https://example.com/img.jpg",
    ): ArticleAsset = ArticleAsset(
        id = "as1",
        articleId = articleId,
        type = type,
        uri = uri,
        checksum = "abc123",
        width = 100,
        height = 100,
        sizeBytes = 2048,
    )
}
