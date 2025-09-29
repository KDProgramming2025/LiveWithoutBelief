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
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.min
import kotlin.random.Random

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
            var contentEntity = articleDao.getArticleContent(articleId)
            if (contentEntity == null) {
                // On-demand fetch when content is missing locally
                val dto = retryWithBackoff { api.getArticle(articleId) }
                if (dto != null) {
                    val plain = dto.text ?: ""
                    val html = dto.html ?: ""
                    val textHash = sha256(plain)
                    val checksumOk = dto.checksum.isBlank() || dto.checksum == textHash
                    if (checksumOk) {
                        val articleEntity = ArticleEntity(
                            id = dto.id,
                            title = dto.title,
                            slug = dto.slug,
                            version = dto.version,
                            updatedAt = dto.updatedAt,
                            wordCount = dto.wordCount,
                        )
                        val newContent = ArticleContentEntity(
                            articleId = dto.id,
                            htmlBody = html,
                            plainText = plain,
                            textHash = textHash,
                            indexUrl = dto.indexUrl,
                        )
                        articleDao.upsertArticleWithContent(articleEntity, newContent)
                        contentEntity = newContent
                    }
                }
            }
            val domain = contentEntity?.toDomain()
            if (domain != null) {
                @Suppress("UNCHECKED_CAST")
                emit(Result.Success(domain) as Result<ArticleContent>)
            } else {
                emit(Result.Error(Exception("Content not found")))
            }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun refreshArticles(): Unit = withContext(Dispatchers.IO) {
        // Fetch manifest with retry/backoff
        val manifest = retryWithBackoff { api.getManifest().items } ?: emptyList()
        if (manifest.isEmpty()) return@withContext

        // Snapshot local state to guide delta decisions
        val localById = articleDao.listArticles().associateBy { it.id }
        // Parallelize detail fetches with bounded concurrency
        // Run detail fetches in parallel and await, but discard returned list to keep Unit
        val awaited = coroutineScope {
            manifest.map { item ->
                async {
                    val articleEntity = ArticleEntity(
                        id = item.id,
                        title = item.title,
                        slug = item.slug,
                        version = item.version,
                        updatedAt = item.updatedAt,
                        wordCount = item.wordCount,
                    )

                    val local = localById[item.id]
                    val hasLocalContent = articleDao.getArticleContent(item.id) != null
                    // If version unchanged and content exists, skip details fetch; still upsert metadata
                    if (local?.version == item.version && hasLocalContent) {
                        articleDao.upsertArticle(articleEntity)
                        return@async
                    }

                    // Fetch details with retry/backoff
                    val dto = retryWithBackoff { api.getArticle(item.id) }
                    if (dto == null) {
                        // Network failed; upsert metadata only
                        articleDao.upsertArticle(articleEntity)
                        return@async
                    }

                    val plain = dto.text ?: ""
                    val html = dto.html ?: ""
                    val textHash = sha256(plain)

                    // Optional checksum verification (assumes checksum is of plain text for now)
                    val checksumOk = dto.checksum.isBlank() || dto.checksum == textHash
                    if (!checksumOk) {
                        // Integrity check failed; don't persist content, but keep metadata
                        articleDao.upsertArticle(articleEntity)
                        return@async
                    }

                    val existing = articleDao.getArticleContent(item.id)
                    if (existing == null || existing.textHash != textHash) {
                        val contentEntity = ArticleContentEntity(
                            articleId = item.id,
                            htmlBody = html,
                            plainText = plain,
                            textHash = textHash,
                            indexUrl = dto.indexUrl,
                        )
                        articleDao.upsertArticleWithContent(articleEntity, contentEntity)
                    } else {
                        // Content unchanged; still ensure article row updated
                        articleDao.upsertArticle(articleEntity)
                    }

                    // Persist media assets (idempotent) and prune removed ones
                    val mediaEntities = dto.media.map { m ->
                        info.lwb.data.repo.db.ArticleAssetEntity(
                            id = m.id,
                            articleId = item.id,
                            type = m.type,
                            uri = m.src ?: (m.filename ?: ""),
                            checksum = m.checksum ?: "",
                            width = null,
                            height = null,
                            sizeBytes = null,
                        )
                    }
                    if (mediaEntities.isNotEmpty()) {
                        articleDao.upsertAssets(mediaEntities)
                        articleDao.pruneAssetsForArticle(item.id, mediaEntities.map { it.id })
                    } else {
                        // No media in payload; prune all existing for this article
                        articleDao.pruneAssetsForArticle(item.id, emptyList())
                    }
                }
            }.awaitAll()
        }
        // After sync, apply eviction policy to limit offline cache footprint.
        applyEvictionPolicy(manifest)
        // ensure Unit return
        Unit
    }

    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest().items }.getOrElse { emptyList() }
    }

    override suspend fun searchLocal(query: String, limit: Int, offset: Int): List<Article> = withContext(
        Dispatchers.IO,
    ) {
        if (query.isBlank()) return@withContext emptyList()
        val rows = articleDao.searchArticlesLike(query.trim(), limit, offset)
        rows.map { Article(it.id, it.title, it.slug, it.version, it.updatedAt, it.wordCount) }
    }

    // Keep the most recent 'KEEP_RECENT_COUNT' articles' content/assets; evict older ones.
    private suspend fun applyEvictionPolicy(manifest: List<ManifestItemDto>) {
        val keepIds = manifest
            .sortedByDescending { it.updatedAt }
            .take(KEEP_RECENT_COUNT)
            .map { it.id }
        try {
            articleDao.deleteContentsNotIn(keepIds)
            articleDao.deleteAssetsNotIn(keepIds)
        } catch (_: Exception) {
            // Avoid failing the whole refresh on eviction issues.
        }
    }
}

// Keep most recent N content cached
private const val KEEP_RECENT_COUNT = 4

private fun ArticleEntity.toDomain() = Article(id, title, slug, version, updatedAt, wordCount)

// Note: Manifest images are not persisted in ArticleEntity; domain mapping from repo-by-label uses DTO directly.
private fun ArticleContentEntity.toDomain() = ArticleContent(articleId, htmlBody, plainText, textHash, indexUrl)

private fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
}

// Retry with exponential backoff and jitter. Returns null if all attempts fail.
private suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 200,
    maxDelayMs: Long = 2000,
    factor: Double = 2.0,
    block: suspend () -> T,
): T? {
    var attempt = 0
    var delayMs = initialDelayMs
    while (attempt < maxAttempts) {
        try {
            return block()
        } catch (_: Exception) {
            attempt++
            if (attempt >= maxAttempts) break
            val jitter = Random.nextLong(0, delayMs / 2 + 1)
            val actual = min(delayMs + jitter, maxDelayMs)
            delay(actual)
            delayMs = min((delayMs * factor).toLong(), maxDelayMs)
        }
    }
    return null
}
