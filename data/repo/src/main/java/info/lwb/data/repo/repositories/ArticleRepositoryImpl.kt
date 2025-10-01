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

/**
 * Concrete implementation of [ArticleRepository] backed by a local Room database and a remote [ArticleApi].
 *
 * Responsibilities:
 *  - Expose reactive streams of article metadata & content (serving cached data first).
 *  - On-demand fetch of missing article content with checksum validation for integrity.
 *  - Periodic/full refresh using the remote manifest (delta style) with bounded parallel detail fetches.
 *  - Media asset upsert + pruning (idempotent) to keep local cache aligned with remote state.
 *  - Simple eviction policy to limit offline footprint to the most recently updated N articles.
 *
 * Concurrency / Threading:
 *  - All blocking I/O is dispatched to [Dispatchers.IO]. Parallel network/content fetches leverage structured
 *    concurrency (coroutineScope + async) enabling cancellation propagation.
 *
 * Failure Handling:
 *  - Individual article detail fetch failures do not abort the entire refresh—metadata upsert still occurs.
 *  - Eviction failures are swallowed to avoid failing the refresh (logged externally if needed later).
 *  - Retry with backoff (see [retryWithBackoff]) is used for manifest and detail retrieval.
 *
 * Integrity:
 *  - Optional checksum verification (plain text) prevents persisting corrupt payloads if mismatch occurs.
 */
class ArticleRepositoryImpl(private val api: ArticleApi, private val articleDao: ArticleDao) : ArticleRepository {
    override fun getArticles(): Flow<Result<List<Article>>> = flow {
        emit(Result.Loading)
        try {
            val articles = articleDao
                .listArticles()
                .map { it.toDomain() }
            @Suppress("UNCHECKED_CAST")
            emit(Result.Success(articles) as Result<List<Article>>)
        } catch (io: java.io.IOException) {
            emit(Result.Error(io))
        } catch (sql: java.sql.SQLException) {
            emit(Result.Error(sql))
        } catch (ise: IllegalStateException) {
            emit(Result.Error(ise))
        }
    }

    override fun getArticleContent(articleId: String): Flow<Result<ArticleContent>> = flow {
        emit(Result.Loading)
        try {
            var contentEntity = articleDao.getArticleContent(articleId)
            if (contentEntity == null) {
                contentEntity = fetchAndPersistContent(
                    articleId = articleId,
                    api = api,
                    articleDao = articleDao,
                )
            }
            val domain = contentEntity?.toDomain()
            if (domain != null) {
                @Suppress("UNCHECKED_CAST")
                emit(Result.Success(domain) as Result<ArticleContent>)
            } else {
                emit(Result.Error(IllegalStateException("Content not found")))
            }
        } catch (io: java.io.IOException) {
            emit(Result.Error(io))
        } catch (sql: java.sql.SQLException) {
            emit(Result.Error(sql))
        } catch (ise: IllegalStateException) {
            emit(Result.Error(ise))
        }
    }

    override suspend fun refreshArticles() {
        val manifest = fetchManifestInternal() ?: return
        if (manifest.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            val localById = snapshotLocalArticles()
            processManifest(
                manifest = manifest,
                localById = localById,
            )
            applyEvictionPolicy(manifest)
        }
    }

    private suspend fun fetchManifestInternal(): List<ManifestItemDto>? = retryWithBackoff {
        api
            .getManifest()
            .items
    }

    private suspend fun snapshotLocalArticles(): Map<String, ArticleEntity> =
        articleDao
            .listArticles()
            .associateBy { it.id }

    private suspend fun processManifest(
        manifest: List<ManifestItemDto>,
        localById: Map<String, ArticleEntity>,
    ) = coroutineScope {
        manifest
            .map { item ->
                async { upsertArticleAndMaybeContent(item, localById[item.id]) }
            }.awaitAll()
    }

    private suspend fun upsertArticleAndMaybeContent(item: ManifestItemDto, local: ArticleEntity?) {
        val articleEntity = ArticleEntity(
            id = item.id,
            title = item.title,
            slug = item.slug,
            version = item.version,
            updatedAt = item.updatedAt,
            wordCount = item.wordCount,
        )
        val hasLocalContent = articleDao.getArticleContent(item.id) != null
        if (local?.version == item.version && hasLocalContent) {
            articleDao.upsertArticle(articleEntity)
            return
        }
        val dto = retryWithBackoff { api.getArticle(item.id) }
        if (dto == null) {
            articleDao.upsertArticle(articleEntity)
            return
        }
        val plain = dto.text ?: ""
        val html = dto.html ?: ""
        val textHash = sha256(plain)
        val checksumOk = dto.checksum.isBlank() || dto.checksum == textHash
        if (!checksumOk) {
            articleDao.upsertArticle(articleEntity)
            return
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
            articleDao.upsertArticle(articleEntity)
        }
        persistAndPruneMedia(dto, item.id)
    }

    private suspend fun persistAndPruneMedia(dto: info.lwb.data.network.ArticleDto, articleId: String) {
        val mediaEntities = dto.media.map { m ->
            info.lwb.data.repo.db.ArticleAssetEntity(
                id = m.id,
                articleId = articleId,
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
            articleDao.pruneAssetsForArticle(articleId, mediaEntities.map { it.id })
        } else {
            articleDao.pruneAssetsForArticle(articleId, emptyList())
        }
    }

    /** Public helper to retrieve the current manifest snapshot without performing a full refresh. */
    suspend fun syncManifest(): List<ManifestItemDto> = withContext(Dispatchers.IO) {
        runCatching { api.getManifest().items }.getOrElse { emptyList() }
    }

    override suspend fun searchLocal(query: String, limit: Int, offset: Int): List<Article> {
        if (query.isBlank()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val rows = articleDao.searchArticlesLike(query.trim(), limit, offset)
            rows.map { Article(it.id, it.title, it.slug, it.version, it.updatedAt, it.wordCount) }
        }
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
        } catch (_: java.sql.SQLException) {
            // Swallow DB pruning issues; refresh already succeeded.
        } catch (_: IllegalStateException) {
            // Swallow DB state issues during eviction.
        }
    }
}

// Keep most recent N content cached
private const val KEEP_RECENT_COUNT = 4

private suspend fun fetchAndPersistContent(
    articleId: String,
    api: ArticleApi,
    articleDao: ArticleDao,
): ArticleContentEntity? {
    val dto = retryWithBackoff { api.getArticle(articleId) } ?: return null
    val plain = dto.text ?: ""
    val html = dto.html ?: ""
    val textHash = sha256(plain)
    val checksumOk = dto.checksum.isBlank() || dto.checksum == textHash
    if (!checksumOk) {
        return null
    }
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
    return newContent
}

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
            if (attempt >= maxAttempts) {
                break
            }
            val jitter = Random.nextLong(0, delayMs / 2 + 1)
            val actual = min(delayMs + jitter, maxDelayMs)
            delay(actual)
            delayMs = min((delayMs * factor).toLong(), maxDelayMs)
        }
    }
    return null
}
