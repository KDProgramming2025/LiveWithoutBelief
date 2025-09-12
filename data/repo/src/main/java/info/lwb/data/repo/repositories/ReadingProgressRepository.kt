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

class ReadingProgressRepository(
    private val dao: ReadingProgressDao,
) {
    fun observe(articleId: String): Flow<ReadingProgress?> = dao.observe(articleId).map { it?.toDomain() }

    suspend fun update(articleId: String, pageIndex: Int, totalPages: Int) {
        val progress = if (totalPages <= 0) 0.0 else (pageIndex.toDouble() / (totalPages - 1).coerceAtLeast(1))
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

data class ReadingProgress(
    val articleId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val progress: Double,
    val updatedAt: String,
)

private fun ReadingProgressEntity.toDomain() = ReadingProgress(articleId, pageIndex, totalPages, progress, updatedAt)
