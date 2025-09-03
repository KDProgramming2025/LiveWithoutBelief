package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ArticleRepositoryImpl(
    private val api: ArticleApi,
    private val articleDao: ArticleDao,
) : ArticleRepository {

    override fun getArticles(): Flow<Result<List<Article>>> = flow {
        emit(Result.Loading)
        try {
            val articles = articleDao.listArticles().map { it.toDomain() }
            @Suppress("UNCHECKED_CAST")
            emit(Result.Success(articles) as Result<List<Article>>)
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override fun getArticleContent(articleId: String): Flow<Result<ArticleContent>> = flow {
        emit(Result.Loading)
        try {
            val content = articleDao.getArticleContent(articleId)?.toDomain()
            if (content != null) {
                @Suppress("UNCHECKED_CAST")
                emit(Result.Success(content) as Result<ArticleContent>)
            } else {
                emit(Result.Error(Exception("Content not found")))
            }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun refreshArticles() = withContext(Dispatchers.IO) {
        // Sync from network
        val manifest = runCatching { api.getManifest() }.getOrDefault(emptyList())
        // For now, just insert stub if empty
    if (articleDao.listArticles().isEmpty()) {
            insertStubArticle()
        }
    }

    private suspend fun insertStubArticle(id: String = UUID.randomUUID().toString()) = withContext(Dispatchers.IO) {
        val article = ArticleEntity(
            id = id,
            title = "Sample", slug = "sample", version = 1, updatedAt = "2025-01-01T00:00:00Z", wordCount = 100
        )
        val content = ArticleContentEntity(articleId = id, htmlBody = "<p>Sample</p>", plainText = "Sample", textHash = "hash")
        articleDao.upsertArticleWithContent(article, content)
    }

    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest() }.getOrElse { emptyList() }
    }
}

private fun ArticleEntity.toDomain() = Article(id, title, slug, version, updatedAt, wordCount)
private fun ArticleContentEntity.toDomain() = ArticleContent(articleId, htmlBody, plainText, textHash)

