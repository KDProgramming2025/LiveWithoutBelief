/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.core.domain.BookmarkRepository
import info.lwb.core.domain.UserSession
import info.lwb.core.model.Article
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import info.lwb.data.repo.db.ArticleEntity
import info.lwb.data.repo.db.BookmarkDao
import info.lwb.data.repo.db.BookmarkEntity
import info.lwb.data.repo.db.BookmarkFolderEntity
import info.lwb.data.repo.db.FolderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Concrete implementation of [BookmarkRepository] backed by local Room DAOs.
 *
 * Responsibilities:
 * - Maps Room entities to domain models.
 * - Ensures operations are scoped to the currently signed-in user.
 * - Generates stable bookmark & folder identifiers.
 */
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: FolderDao,
    private val session: UserSession,
) : BookmarkRepository {
    private fun userIdOrThrow(): String = session.currentUserId() ?: error("Not signed in")

    override fun getBookmarks(): Flow<Result<List<Bookmark>>> {
        val userId = userIdOrThrow()
        val flow = bookmarkDao.observeBookmarks(userId)
        return flow
            .map { list ->
                val domainList = list
                    .map { it.toDomain() }
                Result.Success(domainList)
            }
    }

    override fun getBookmarkFolders(): Flow<Result<List<BookmarkFolder>>> {
        val userId = userIdOrThrow()
        val flow = folderDao.observeFolders(userId)
        return flow
            .map { list ->
                val domainList = list
                    .map { it.toDomain() }
                Result.Success(domainList)
            }
    }

    override suspend fun addBookmark(articleId: String, folderId: String?): Result<Unit> {
        val userId = userIdOrThrow()
        val now = Instant.now().toString()
        val id = "$userId:$articleId" // stable id per user-article
        val outcome = runCatching {
            bookmarkDao.upsertBookmark(
                BookmarkEntity(
                    id = id,
                    userId = userId,
                    articleId = articleId,
                    folderId = folderId,
                    createdAt = now,
                ),
            )
        }
        val result = outcome.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { e -> Result.Error(e) },
        )
        return result
    }

    override suspend fun removeBookmark(bookmarkId: String): Result<Unit> {
        val userId = userIdOrThrow()
        // We only know id here; build entity shell for @Delete
        val outcome = runCatching {
            bookmarkDao.deleteBookmark(
                BookmarkEntity(
                    id = bookmarkId,
                    userId = userId,
                    articleId = bookmarkId.substringAfter(':'),
                    folderId = null,
                    createdAt = "",
                ),
            )
        }
        val result = outcome.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { e -> Result.Error(e) },
        )
        return result
    }

    override suspend fun createFolder(name: String): Result<String> {
        val userId = userIdOrThrow()
        val existing = folderDao.findByName(userId, name)
        val id = if (existing != null) {
            existing.id
        } else {
            val newId = "$userId:folder:${UUID.randomUUID()}"
            val now = Instant.now().toString()
            val outcomeInsert = runCatching {
                folderDao.upsert(
                    BookmarkFolderEntity(
                        id = newId,
                        userId = userId,
                        name = name,
                        createdAt = now,
                    ),
                )
            }
            outcomeInsert.exceptionOrNull()?.let { return Result.Error(it) }
            newId
        }
        return Result.Success(id)
    }

    override suspend fun moveBookmark(bookmarkId: String, folderId: String?): Result<Unit> {
        val userId = userIdOrThrow()
        // Load current bookmark row
        // No direct get in DAO; upsert by constructing new entity with same id
        val outcome = runCatching {
            bookmarkDao.upsertBookmark(
                BookmarkEntity(
                    id = bookmarkId,
                    userId = userId,
                    articleId = bookmarkId.substringAfter(':'),
                    folderId = folderId,
                    createdAt = Instant.now().toString(),
                ),
            )
        }
        val result = outcome.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { e -> Result.Error(e) },
        )
        return result
    }

    override suspend fun searchBookmarked(query: String, limit: Int, offset: Int): List<Article> {
        val userId = userIdOrThrow()
        val rows = bookmarkDao.searchBookmarkedArticles(userId, query, limit, offset)
        return rows
            .map { it.toDomain() }
    }
}

private fun BookmarkEntity.toDomain() = Bookmark(
    id = id,
    articleId = articleId,
    folderId = folderId,
    createdAt = createdAt,
)

private fun BookmarkFolderEntity.toDomain() = BookmarkFolder(
    id = id,
    name = name,
    createdAt = createdAt,
)

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
)
