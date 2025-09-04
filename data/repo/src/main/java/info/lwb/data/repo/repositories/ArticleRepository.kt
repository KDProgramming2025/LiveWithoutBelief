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
        // Fetch manifest (ignore failure silently for now)
        val manifest = runCatching { api.getManifest() }.getOrElse { emptyList() }
        if (manifest.isEmpty()) return@withContext
        // Insert/update each manifest item if not already stored (no content yet)
        manifest.forEach { item ->
            val entity = ArticleEntity(
                id = item.id,
                title = item.title,
                slug = item.slug,
                version = item.version,
                updatedAt = item.updatedAt,
                wordCount = item.wordCount
            )
            articleDao.upsertArticle(entity)
        }
    }

    // Future: separate syncManifest that diffs and deletes missing.

    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest() }.getOrElse { emptyList() }
    }
}

private fun ArticleEntity.toDomain() = Article(id, title, slug, version, updatedAt, wordCount)
private fun ArticleContentEntity.toDomain() = ArticleContent(articleId, htmlBody, plainText, textHash)

