/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.data.network.ArticleApi
import java.security.MessageDigest
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
        val manifest = runCatching { api.getManifest() }.getOrElse { emptyList() }
        if (manifest.isEmpty()) return@withContext
        manifest.forEach { item ->
            // Upsert manifest item as lightweight article row
            val articleEntity = ArticleEntity(
                id = item.id,
                title = item.title,
                slug = item.slug,
                version = item.version,
                updatedAt = item.updatedAt,
                wordCount = item.wordCount,
            )
            // Fetch full article to sync content delta if changed
            val dto = runCatching { api.getArticle(item.id) }.getOrNull()
            if (dto != null) {
                val plain = dto.text ?: ""
                val html = dto.html ?: ""
                val textHash = sha256(plain)
                val existing = articleDao.getArticleContent(item.id)
                if (existing == null || existing.textHash != textHash) {
                    val contentEntity = ArticleContentEntity(
                        articleId = item.id,
                        htmlBody = html,
                        plainText = plain,
                        textHash = textHash,
                    )
                    articleDao.upsertArticleWithContent(articleEntity, contentEntity)
                } else {
                    // Content unchanged; still ensure article row updated
                    articleDao.upsertArticle(articleEntity)
                }
            } else {
                // Fallback: only upsert article metadata
                articleDao.upsertArticle(articleEntity)
            }
        }
    }

    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest() }.getOrElse { emptyList() }
    }

    override suspend fun searchLocal(query: String, limit: Int, offset: Int): List<Article> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val rows = articleDao.searchArticlesLike(query.trim(), limit, offset)
        rows.map { Article(it.id, it.title, it.slug, it.version, it.updatedAt, it.wordCount) }
    }
}

private fun ArticleEntity.toDomain() = Article(id, title, slug, version, updatedAt, wordCount)
private fun ArticleContentEntity.toDomain() = ArticleContent(articleId, htmlBody, plainText, textHash)

private fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
}
