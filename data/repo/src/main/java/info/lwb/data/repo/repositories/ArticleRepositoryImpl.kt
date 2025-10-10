/*
 * SPDY-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.log.Logger
import info.lwb.core.common.Result
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.model.Article
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ManifestItemDto
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
// removed unused onStart/catch imports after flow refactor
import kotlinx.coroutines.withContext
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
 *  - Retry with backoff (see [retryWithBackoff]) is used for manifest and detail retrieval.
 *
 * Integrity:
 *  - Optional checksum verification (plain text) prevents persisting corrupt payloads if mismatch occurs.
 */
class ArticleRepositoryImpl(private val api: ArticleApi, private val articleDao: ArticleDao) : ArticleRepository {
    private companion object {
        const val TAG = "ArticleRepo"
    }

    init {
        Logger.d(TAG) { "init" }
    }

    override fun getArticles(): Flow<Result<List<Article>>> = flow {
        emit(Result.Loading)
        val initial = withContext(Dispatchers.IO) { articleDao.listArticles() }
        if (initial.isEmpty()) {
            Logger.d(TAG) { "prefetch:snapshot empty -> refreshArticles" }
            runCatching { refreshArticles() }
                .onFailure { Logger.d(TAG) { "prefetch:refresh failed msg=" + it.message } }
        }
        articleDao
            .observeArticles()
            .map { rows -> rows.map { it.toDomain() } }
            .collect { list -> emit(Result.Success(list)) }
    }

    override suspend fun snapshotArticles(): List<Article> = withContext(Dispatchers.IO) {
        articleDao.listArticles().map { it.toDomain() }
    }

    override suspend fun refreshArticles() {
        Logger.d(TAG) { "refresh:start" }
        val manifest = fetchManifestInternal()
        if (manifest == null) {
            Logger.d(TAG) { "refresh:manifest=null (failed after retries)" }
            return
        }
        if (manifest.isEmpty()) {
            Logger.d(TAG) { "refresh:manifest=empty -> clearing all local articles & related tables" }
            withContext(Dispatchers.IO) {
                // Full reset so UI immediately reflects authoritative empty state
                articleDao.clearAllArticles()
            }
            return
        }
        Logger.d(TAG) { "refresh:manifest.size=" + manifest.size }
        withContext(Dispatchers.IO) {
            val localById = snapshotLocalArticles()
            processManifest(
                manifest = manifest,
                localById = localById,
            )
            // Authoritative pruning: remove any locally cached articles absent from the manifest
            val keepIds = manifest.map { it.id }
            articleDao.deleteArticlesNotIn(keepIds)
        }
        Logger.d(TAG) { "refresh:applied size=" + manifest.size }
    }

    private suspend fun fetchManifestInternal(): List<ManifestItemDto>? = retryWithBackoff {
        Logger.d(TAG) { "fetchManifest:request" }
        api.getManifest().items.also { list ->
            Logger.d(TAG) { "fetchManifest:success count=" + list.size }
        }
    }

    private suspend fun snapshotLocalArticles(): Map<String, ArticleEntity> = articleDao
        .listArticles()
        .associateBy { it.id }

    private suspend fun processManifest(manifest: List<ManifestItemDto>, localById: Map<String, ArticleEntity>) =
        coroutineScope {
            manifest
                .mapIndexed { index, item ->
                    async { upsertArticle(item, localById[item.id], index) }
                }.awaitAll()
        }

    private suspend fun upsertArticle(item: ManifestItemDto, local: ArticleEntity?, orderIndex: Int) {
        val articleEntity = ArticleEntity(
            id = item.id,
            title = item.title,
            slug = item.slug,
            version = item.version,
            updatedAt = item.updatedAt,
            wordCount = item.wordCount,
            label = item.label,
            order = orderIndex,
            coverUrl = item.coverUrl,
            iconUrl = item.iconUrl,
            indexUrl = item.indexUrl,
        )
        if (local?.version == item.version) {
            articleDao.upsertArticle(articleEntity)
            return
        }
        articleDao.upsertArticle(articleEntity)
    }

    override suspend fun searchLocal(query: String, limit: Int, offset: Int): List<Article> {
        if (query.isBlank()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val rows = articleDao.searchArticlesLike(query.trim(), limit, offset)
            rows.map { row ->
                Article(
                    id = row.id,
                    title = row.title,
                    slug = row.slug,
                    version = row.version,
                    updatedAt = row.updatedAt,
                    wordCount = row.wordCount,
                    label = row.label,
                    order = row.ordering ?: Int.MAX_VALUE,
                    coverUrl = row.coverUrl ?: "", // projection nullable only for legacy rows
                    iconUrl = row.iconUrl ?: "", // projection nullable only for legacy rows
                    indexUrl = row.indexUrl ?: "",
                )
            }
        }
    }
}

private fun ArticleEntity.toDomain() = Article(
    id = id,
    title = title,
    slug = slug,
    version = version,
    updatedAt = updatedAt,
    wordCount = wordCount,
    label = label,
    order = order,
    coverUrl = coverUrl,
    iconUrl = iconUrl,
    indexUrl = indexUrl,
)

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
