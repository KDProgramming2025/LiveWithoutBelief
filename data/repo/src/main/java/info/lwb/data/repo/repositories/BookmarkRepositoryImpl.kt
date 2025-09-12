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
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: FolderDao,
    private val session: UserSession,
) : BookmarkRepository {

    private fun userIdOrThrow(): String = session.currentUserId() ?: error("Not signed in")

    override fun getBookmarks(): Flow<Result<List<Bookmark>>> {
        val userId = userIdOrThrow()
        return bookmarkDao.observeBookmarks(userId).map { list ->
            Result.Success(list.map { it.toDomain() })
        }
    }

    override fun getBookmarkFolders(): Flow<Result<List<BookmarkFolder>>> {
        val userId = userIdOrThrow()
        return folderDao.observeFolders(userId).map { list ->
            Result.Success(list.map { it.toDomain() })
        }
    }

    override suspend fun addBookmark(articleId: String, folderId: String?): Result<Unit> = runCatching {
        val userId = userIdOrThrow()
        val now = java.time.Instant.now().toString()
        val id = "$userId:$articleId" // stable id per user-article
        bookmarkDao.upsertBookmark(
            BookmarkEntity(
                id = id,
                userId = userId,
                articleId = articleId,
                folderId = folderId,
                createdAt = now,
            ),
        )
    }.fold({ Result.Success(Unit) }, { e -> Result.Error(e) })

    override suspend fun removeBookmark(bookmarkId: String): Result<Unit> = runCatching {
        val userId = userIdOrThrow()
        // We only know id here; build entity shell for @Delete
        bookmarkDao.deleteBookmark(
            BookmarkEntity(
                id = bookmarkId,
                userId = userId,
                articleId = bookmarkId.substringAfter(':'),
                folderId = null,
                createdAt = "",
            ),
        )
    }.fold({ Result.Success(Unit) }, { e -> Result.Error(e) })

    override suspend fun createFolder(name: String): Result<String> = runCatching {
        val userId = userIdOrThrow()
        val existing = folderDao.findByName(userId, name)
        if (existing != null) return@runCatching existing.id
        val id = "$userId:folder:${java.util.UUID.randomUUID()}"
        val now = java.time.Instant.now().toString()
        folderDao.upsert(BookmarkFolderEntity(id = id, userId = userId, name = name, createdAt = now))
        id
    }.fold({ Result.Success(it) }, { e -> Result.Error(e) })

    override suspend fun moveBookmark(bookmarkId: String, folderId: String?): Result<Unit> = runCatching {
        val userId = userIdOrThrow()
        // Load current bookmark row
        // No direct get in DAO; upsert by constructing new entity with same id
        bookmarkDao.upsertBookmark(
            BookmarkEntity(
                id = bookmarkId,
                userId = userId,
                articleId = bookmarkId.substringAfter(':'),
                folderId = folderId,
                createdAt = java.time.Instant.now().toString(),
            ),
        )
    }.fold({ Result.Success(Unit) }, { e -> Result.Error(e) })

    override suspend fun searchBookmarked(query: String, limit: Int, offset: Int): List<Article> {
        val userId = userIdOrThrow()
        return bookmarkDao.searchBookmarkedArticles(userId, query, limit, offset).map { it.toDomain() }
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
)
