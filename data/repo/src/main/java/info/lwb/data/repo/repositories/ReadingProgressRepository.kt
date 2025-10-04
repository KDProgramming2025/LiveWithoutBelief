/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.data.repo.db.ReadingProgressDao
import info.lwb.data.repo.db.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Repository exposing read/write access to persisted reading progress for paginated article content.
 *
 * The progress value is normalized to the inclusive range [0.0, 1.0]. When [totalPages] is zero or negative
 * progress is treated as 0.0. For a single page (totalPages == 1) progress is 0.0 at pageIndex 0 and coerced
 * such that division by zero is avoided.
 */
class ReadingProgressRepository(private val dao: ReadingProgressDao) {
    /**
     * Observe progress updates for the given [articleId]. Emits null if no stored progress exists yet.
     */
    fun observe(articleId: String): Flow<ReadingProgress?> = dao.observe(articleId).map { entity -> entity?.toDomain() }

    /**
     * Persist (upsert) the current reading position defined by [pageIndex] within [totalPages].
     *
     * [pageIndex] is assumed zero-based. [totalPages] values less than or equal to zero produce progress 0.0.
     */
    suspend fun update(articleId: String, pageIndex: Int, totalPages: Int) {
        val progress = if (totalPages <= 0) {
            0.0
        } else {
            pageIndex.toDouble() / (totalPages - 1).coerceAtLeast(1)
        }
        val entity = ReadingProgressEntity(
            articleId = articleId,
            pageIndex = pageIndex,
            totalPages = totalPages,
            progress = progress.coerceIn(0.0, 1.0),
            updatedAt = Instant.now().toString(),
        )
        dao.upsert(entity)
    }
}

/**
 * Domain model summarizing persisted reading progress for an article.
 *
 * @property articleId Unique identifier of the article.
 * @property pageIndex Zero-based index of the current page.
 * @property totalPages Total page count for the article snapshot when measured.
 * @property progress Normalized progress in range [0.0, 1.0].
 * @property updatedAt ISO-8601 instant string of last update.
 */
data class ReadingProgress(
    val articleId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val progress: Double,
    val updatedAt: String,
)

private fun ReadingProgressEntity.toDomain() = ReadingProgress(articleId, pageIndex, totalPages, progress, updatedAt)
