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
import info.lwb.core.model.MenuItem
import info.lwb.core.model.ReadingProgress
import info.lwb.core.model.ThreadMessage
import kotlinx.coroutines.flow.Flow

/**
 * Primary entry point for article domain data (list + detailed content + local search cache).
 * Implementations are expected to coordinate local persistence and remote synchronization.
 */
interface ArticleRepository {
    /** Emits the current list of articles (cached + remote refresh errors wrapped in [Result]). */
    fun getArticles(): Flow<Result<List<Article>>>

    /** Returns a one-shot snapshot of currently cached articles (no remote call). */
    suspend fun snapshotArticles(): List<Article>

    /** Emits the full parsed content (pages/blocks) for a given [articleId]. */
    fun getArticleContent(articleId: String): Flow<Result<ArticleContent>>

    /** Triggers a foreground refresh of the article index (errors surfaced in [getArticles]). */
    suspend fun refreshArticles()

    /**
     * Performs a local (offline) search over indexed article metadata.
     * @param query Case-insensitive substring or token query.
     * @param limit Maximum results to return (default 25).
     * @param offset Pagination offset (default 0).
     */
    suspend fun searchLocal(query: String, limit: Int = 25, offset: Int = 0): List<Article>
}

// Removed separate LabelArticleRepository – functionality unified in ArticleRepository via filtering helpers.

/**
 * Manages user bookmarks and folders. Implementations persist state locally and/or remotely.
 */
interface BookmarkRepository {
    /** Stream of all bookmarks with loading/error encapsulated. */
    fun getBookmarks(): Flow<Result<List<Bookmark>>>

    /** Stream of bookmark folders (hierarchy is flat for simplicity). */
    fun getBookmarkFolders(): Flow<Result<List<BookmarkFolder>>>

    /** Adds a bookmark for an article optionally into a folder. */
    suspend fun addBookmark(articleId: String, folderId: String?): Result<Unit>

    /** Removes a bookmark by id. */
    suspend fun removeBookmark(bookmarkId: String): Result<Unit>

    /** Creates a folder returning its generated id. */
    suspend fun createFolder(name: String): Result<String>

    /** Moves an existing bookmark into (or out of) a folder. */
    suspend fun moveBookmark(bookmarkId: String, folderId: String?): Result<Unit>

    /** Local / cached search constrained to bookmarked articles only. */
    suspend fun searchBookmarked(query: String, limit: Int = 25, offset: Int = 0): List<Article>
}

/**
 * Handles user-private annotations and their per-annotation discussion threads.
 * All data is private to the creating user (no shared visibility yet).
 */
interface AnnotationRepository {
    /** Stream of annotations for an article (add/remove/update events cause re-emission). */
    fun getAnnotations(articleId: String): Flow<Result<List<Annotation>>>

    /** Stream of thread messages for a single annotation. */
    fun getThreadMessages(annotationId: String): Flow<Result<List<ThreadMessage>>>

    /**
     * Adds a new annotation defined by text span start/end offsets and an [anchorHash].
     * @return Result containing new annotation id on success.
     */
    suspend fun addAnnotation(articleId: String, startOffset: Int, endOffset: Int, anchorHash: String): Result<String>

    /** Adds a message (different [type] variants) to an existing annotation thread. */
    suspend fun addThreadMessage(annotationId: String, type: String, contentRef: String): Result<String>
}

/** Persists and observes per-article reading progress (page index & total pages snapshot). */
interface ReadingProgressRepository {
    /** Emits progress updates for a specific article or null if none recorded. */
    fun observe(articleId: String): Flow<ReadingProgress?>

    /** Upserts the current page index and total pages for an article. */
    suspend fun update(articleId: String, pageIndex: Int, totalPages: Int)
}

/** Lightweight user identity provider for repositories. Implemented in app layer via AuthFacade. */
interface UserSession {
    /** Returns the current authenticated user id or null if signed out. */
    fun currentUserId(): String?
}

/** Provides the dynamic navigation / category menu items shown in the app. */
interface MenuRepository {
    /** Stream of current menu items (cached + remote refresh errors wrapped). */
    fun getMenuItems(): Flow<Result<List<MenuItem>>>

    /** Forces a remote fetch of the menu manifest. */
    suspend fun refreshMenu()
}
