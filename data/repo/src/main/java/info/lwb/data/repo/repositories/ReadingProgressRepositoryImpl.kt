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
import info.lwb.core.domain.ReadingProgressRepository as DomainRepo
import info.lwb.core.model.ReadingProgress as DomainModel

class ReadingProgressRepositoryImpl(
    private val dao: ReadingProgressDao,
) : DomainRepo {
    override fun observe(articleId: String): Flow<DomainModel?> = dao.observe(articleId).map { it?.toDomain() }

    override suspend fun update(articleId: String, pageIndex: Int, totalPages: Int) {
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

private fun ReadingProgressEntity.toDomain() = DomainModel(articleId, pageIndex, totalPages, progress, updatedAt)
