package info.lwb.data.repo.repositories

import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ArticleRepository(
    private val api: ArticleApi,
    private val articleDao: ArticleDao,
) {
    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest() }.getOrElse { emptyList() }
    }

    suspend fun insertStubArticle(id: String = UUID.randomUUID().toString()) = withContext(Dispatchers.IO) {
        val article = ArticleEntity(
            id = id,
            title = "Sample", slug = "sample", version = 1, updatedAt = "2025-01-01T00:00:00Z", wordCount = 100
        )
        val content = ArticleContentEntity(articleId = id, htmlBody = "<p>Sample</p>", plainText = "Sample", textHash = "hash")
        articleDao.upsertArticleWithContent(article, content)
    }
}

