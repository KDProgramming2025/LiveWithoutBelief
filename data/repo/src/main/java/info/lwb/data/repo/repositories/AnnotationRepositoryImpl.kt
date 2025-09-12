/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.UserSession
import info.lwb.core.model.Annotation
import info.lwb.core.model.ThreadMessage
import info.lwb.data.repo.db.AnnotationDao
import info.lwb.data.repo.db.AnnotationEntity
import info.lwb.data.repo.db.ThreadMessageDao
import info.lwb.data.repo.db.ThreadMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val threadDao: ThreadMessageDao,
    private val session: UserSession,
) : AnnotationRepository {

    override fun getAnnotations(articleId: String): Flow<Result<List<Annotation>>> =
        session.currentUserId()?.let { user ->
            annotationDao.observeAnnotations(user, articleId).map { list ->
                Result.Success(list.map { it.toModel() }) as Result<List<Annotation>>
            }
        } ?: flowOf(Result.Error(IllegalStateException("unauthenticated")))

    override fun getThreadMessages(annotationId: String): Flow<Result<List<ThreadMessage>>> =
        threadDao.observeMessages(annotationId).map { list ->
            Result.Success(list.map { it.toModel() })
        }

    override suspend fun addAnnotation(
        articleId: String,
        startOffset: Int,
        endOffset: Int,
        anchorHash: String,
    ): Result<String> {
        val user = session.currentUserId() ?: return Result.Error(IllegalStateException("unauthenticated"))
        val id = UUID.randomUUID().toString()
        val now = nowIso()
        val entity = AnnotationEntity(
            id = id,
            userId = user,
            articleId = articleId,
            startOffset = startOffset,
            endOffset = endOffset,
            anchorHash = anchorHash,
            createdAt = now,
        )
        annotationDao.add(entity)
        return Result.Success(id)
    }

    override suspend fun addThreadMessage(annotationId: String, type: String, contentRef: String): Result<String> {
        val user = session.currentUserId() ?: return Result.Error(IllegalStateException("unauthenticated"))
        val id = UUID.randomUUID().toString()
        val now = nowIso()
        val entity = ThreadMessageEntity(
            id = id,
            annotationId = annotationId,
            userId = user,
            type = type,
            contentRef = contentRef,
            createdAt = now,
        )
        threadDao.add(entity)
        return Result.Success(id)
    }
}

private fun AnnotationEntity.toModel() = Annotation(
    id = id,
    articleId = articleId,
    startOffset = startOffset,
    endOffset = endOffset,
    anchorHash = anchorHash,
    createdAt = createdAt,
)

private fun ThreadMessageEntity.toModel() = ThreadMessage(
    id = id,
    annotationId = annotationId,
    type = type,
    contentRef = contentRef,
    createdAt = createdAt,
)

private fun nowIso(): String = java.time.OffsetDateTime.now().toString()
