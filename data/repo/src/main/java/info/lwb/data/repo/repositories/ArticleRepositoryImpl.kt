/*
 * SPDY-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import android.util.Log
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
// removed unused onStart/catch imports after flow refactor
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
    private companion object {
        const val TAG = "ArticleRepo"
    }

    init {
        Log.d(TAG, "init")
    }

    override fun getArticles(): Flow<Result<List<Article>>> = flow {
        emit(Result.Loading)
        // One-time prefetch: if database empty at first subscription attempt a remote refresh.
        val initial = articleDao.listArticles()
        if (initial.isEmpty()) {
            Log.d(TAG, "prefetch:empty-db -> triggering refresh before collection")
            runCatching { refreshArticles() }
                .onFailure { Log.d(TAG, "prefetch:refresh failed msg=" + it.message) }
        }
        articleDao
            .observeArticles()
            .map { rows -> rows.map { it.toDomain() } }
            .collect { list ->
                emit(Result.Success(list))
            }
    }

    override suspend fun snapshotArticles(): List<Article> = withContext(Dispatchers.IO) {
        articleDao.listArticles().map { it.toDomain() }
    }

    override fun getArticleContent(articleId: String): Flow<Result<ArticleContent>> = flow {
        emit(Result.Loading)
        try {
            var contentEntity = articleDao.getArticleContent(articleId)
            if (contentEntity == null) {
                contentEntity = fetchAndPersistContent(articleId, api, articleDao)
            }
            val domain = contentEntity?.toDomain()
            if (domain != null) {
                emit(Result.Success(domain))
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
        Log.d(TAG, "refresh:start")
        val manifest = fetchManifestInternal()
        if (manifest == null) {
            Log.d(TAG, "refresh:manifest=null (failed after retries)")
            return
        }
        if (manifest.isEmpty()) {
            Log.d(TAG, "refresh:manifest=empty")
            return
        }
        Log.d(TAG, "refresh:manifest.size=" + manifest.size)
        withContext(Dispatchers.IO) {
            val localById = snapshotLocalArticles()
            processManifest(
                manifest = manifest,
                localById = localById,
            )
            applyEvictionPolicy(manifest)
        }
        Log.d(TAG, "refresh:applied size=" + manifest.size)
    }

    private suspend fun fetchManifestInternal(): List<ManifestItemDto>? = retryWithBackoff {
        Log.d(TAG, "fetchManifest:request")
        api.getManifest().items.also { list ->
            Log.d(TAG, "fetchManifest:success count=" + list.size)
        }
    }

    private suspend fun snapshotLocalArticles(): Map<String, ArticleEntity> = articleDao
        .listArticles()
        .associateBy { it.id }

    private suspend fun processManifest(manifest: List<ManifestItemDto>, localById: Map<String, ArticleEntity>) =
        coroutineScope {
            manifest
                .mapIndexed { index, item ->
                    async { upsertArticleAndMaybeContent(item, localById[item.id], index) }
                }.awaitAll()
        }

    private suspend fun upsertArticleAndMaybeContent(item: ManifestItemDto, local: ArticleEntity?, orderIndex: Int) {
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

    private suspend fun fetchAndPersistContent(
        articleId: String,
        api: ArticleApi,
        articleDao: ArticleDao,
    ): ArticleContentEntity? {
        Log.d(TAG, "fetch id=$articleId")
        val dto = retryWithBackoff { api.getArticle(articleId) }
        if (dto == null) {
            Log.d(TAG, "fetchNull id=$articleId")
            return null
        }
        val plain = dto.text ?: ""
        val html = dto.html ?: ""
        val textHash = sha256(plain)
        val checksumOk = dto.checksum.isBlank() || dto.checksum == textHash
        if (!checksumOk) {
            Log.d(TAG, "checksumFail id=$articleId")
            return null
        }
        // Attempt to preserve existing metadata for artwork / ordering if we only fetched content.
        val existing = articleDao.getArticle(articleId)
        val articleEntity = ArticleEntity(
            id = dto.id,
            title = dto.title,
            slug = dto.slug,
            version = dto.version,
            updatedAt = dto.updatedAt,
            wordCount = dto.wordCount,
            label = existing?.label,
            order = existing?.order ?: Int.MAX_VALUE,
            coverUrl = existing?.coverUrl ?: "",
            iconUrl = existing?.iconUrl ?: "",
            indexUrl = existing?.indexUrl ?: (dto.indexUrl ?: ""),
        )
        val newContent = ArticleContentEntity(
            articleId = dto.id,
            htmlBody = html,
            plainText = plain,
            textHash = textHash,
            indexUrl = dto.indexUrl,
        )
        articleDao.upsertArticleWithContent(articleEntity, newContent)
        Log.d(
            TAG,
            "persisted id=" + articleId + " hasIndexUrl=" + !newContent.indexUrl.isNullOrBlank(),
        )
        return newContent
    }
}

// Exposed constant at top-level (if referenced elsewhere)
private const val KEEP_RECENT_COUNT = 4

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
