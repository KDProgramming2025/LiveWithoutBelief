/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.bookmarks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.AddBookmarkUseCase
import info.lwb.core.domain.CreateFolderUseCase
import info.lwb.core.domain.GetBookmarkFoldersUseCase
import info.lwb.core.domain.GetBookmarksUseCase
import info.lwb.core.domain.MoveBookmarkUseCase
import info.lwb.core.domain.RemoveBookmarkUseCase
import info.lwb.core.domain.SearchBookmarkedUseCase
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel coordinating bookmark data (bookmarks list, folders and search results).
 *
 * Responsibilities:
 * - Observes streams of bookmarks and folders from domain layer use cases.
 * - Exposes immutable state (Compose `mutableStateOf` backed vars with private setters).
 * - Provides user intent handlers for adding, removing, moving bookmarks, folder creation and searching.
 * - Surfaces transient errors through [errorMessage] (future: replace with structured UI events).
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getBookmarks: GetBookmarksUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val removeBookmark: RemoveBookmarkUseCase,
    private val getFolders: GetBookmarkFoldersUseCase,
    private val createFolder: CreateFolderUseCase,
    private val moveBookmark: MoveBookmarkUseCase,
    private val searchBookmarked: SearchBookmarkedUseCase,
) : androidx.lifecycle.ViewModel() {
    /** Current list of saved bookmarks (updated via a flow collector in [init]). */
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set

    /** Current list of bookmark folders. */
    var folders by mutableStateOf<List<BookmarkFolder>>(emptyList())
        private set

    /** Latest search results for the active [search] query. */
    var searchResults by mutableStateOf<List<info.lwb.core.model.Article>>(emptyList())
        private set

    /** Simple placeholder for error propagation (null when no error). */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Collect bookmarks
        viewModelScope.launch {
            getBookmarks().collectLatest { res ->
                when (res) {
                    is Result.Success -> {
                        bookmarks = res.data
                    }
                    is Result.Error -> {
                        errorMessage = res.throwable.message ?: "Bookmark load failed"
                    }
                    Result.Loading -> {
                        // ignored for now
                    }
                }
            }
        }
        // Collect folders
        viewModelScope.launch {
            getFolders().collectLatest { res ->
                when (res) {
                    is Result.Success -> {
                        folders = res.data
                    }
                    is Result.Error -> {
                        errorMessage = res.throwable.message ?: "Folder load failed"
                    }
                    Result.Loading -> {
                    }
                }
            }
        }
    }

    /** Adds a bookmark for the given [articleId] into an optional folder (currently null). */
    fun add(articleId: String) {
        if (articleId.isBlank()) {
            return
        }
        viewModelScope.launch { addBookmark(articleId, folderId = null) }
    }

    /** Removes the specified bookmark [b]. */
    fun remove(b: Bookmark) {
        viewModelScope.launch { removeBookmark(b.id) }
    }

    /**
     * Creates a new folder with [name]. Invokes [onCreated] with the folder id when successful.
     * No-op if name is blank.
     */
    fun newFolder(name: String, onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) {
            return
        }
        viewModelScope.launch {
            val id = (createFolder(name) as? Result.Success)?.data
            if (id != null) {
                onCreated(id)
            }
        }
    }

    /** Moves bookmark [b] into [folderId] (or root if null). */
    fun move(b: Bookmark, folderId: String?) {
        viewModelScope.launch { moveBookmark(b.id, folderId) }
    }

    /** Updates [searchResults] with results for [query]. Blank query clears results. */
    fun search(query: String) {
        viewModelScope.launch {
            searchResults = if (query.isBlank()) {
                emptyList()
            } else {
                searchBookmarked(query)
            }
        }
    }
}
