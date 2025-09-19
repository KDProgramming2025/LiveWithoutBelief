/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Annotation
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import info.lwb.core.model.ReadingProgress
import info.lwb.core.model.ThreadMessage
import info.lwb.core.model.MenuItem
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getArticles(): Flow<Result<List<Article>>>
    fun getArticleContent(articleId: String): Flow<Result<ArticleContent>>
    suspend fun refreshArticles()
    suspend fun searchLocal(query: String, limit: Int = 25, offset: Int = 0): List<Article>
}

interface BookmarkRepository {
    fun getBookmarks(): Flow<Result<List<Bookmark>>>
    fun getBookmarkFolders(): Flow<Result<List<BookmarkFolder>>>
    suspend fun addBookmark(articleId: String, folderId: String?): Result<Unit>
    suspend fun removeBookmark(bookmarkId: String): Result<Unit>
    suspend fun createFolder(name: String): Result<String>
    suspend fun moveBookmark(bookmarkId: String, folderId: String?): Result<Unit>
    suspend fun searchBookmarked(query: String, limit: Int = 25, offset: Int = 0): List<Article>
}

interface AnnotationRepository {
    fun getAnnotations(articleId: String): Flow<Result<List<Annotation>>>
    fun getThreadMessages(annotationId: String): Flow<Result<List<ThreadMessage>>>
    suspend fun addAnnotation(articleId: String, startOffset: Int, endOffset: Int, anchorHash: String): Result<String>
    suspend fun addThreadMessage(annotationId: String, type: String, contentRef: String): Result<String>
}

interface ReadingProgressRepository {
    fun observe(articleId: String): Flow<ReadingProgress?>
    suspend fun update(articleId: String, pageIndex: Int, totalPages: Int)
}

/** Lightweight user identity provider for repositories. Implemented in app layer via AuthFacade. */
interface UserSession {
    fun currentUserId(): String?
}

interface MenuRepository {
    fun getMenuItems(): Flow<Result<List<MenuItem>>>
    suspend fun refreshMenu()
}
