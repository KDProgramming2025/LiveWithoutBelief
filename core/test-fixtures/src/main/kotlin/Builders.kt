package info.lwb.testfixtures

import info.lwb.core.model.*

object Builders {
    fun article(id: String = "a1", title: String = "Title", slug: String = "title", version: Int = 1): Article =
        Article(id, title, slug, version, updatedAt = "2025-01-01T00:00:00Z", wordCount = 1000)

    fun content(articleId: String = "a1"): ArticleContent = ArticleContent(
        articleId = articleId,
        htmlBody = "<p>Hello</p>",
        plainText = "Hello",
        textHash = "deadbeef"
    )

    fun asset(articleId: String = "a1", type: String = "image", uri: String = "https://example.com/img.jpg"): ArticleAsset =
        ArticleAsset(
            id = "as1",
            articleId = articleId,
            type = type,
            uri = uri,
            checksum = "abc123",
            width = 100,
            height = 100,
            sizeBytes = 2048
        )
}
