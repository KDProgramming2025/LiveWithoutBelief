/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.lwb.auth.AuthFacade
import info.lwb.core.domain.AddBookmarkUseCase
import info.lwb.core.domain.BookmarkRepository
import info.lwb.core.domain.CreateFolderUseCase
import info.lwb.core.domain.GetBookmarkFoldersUseCase
import info.lwb.core.domain.GetBookmarksUseCase
import info.lwb.core.domain.MoveBookmarkUseCase
import info.lwb.core.domain.RemoveBookmarkUseCase
import info.lwb.core.domain.SearchBookmarkedUseCase
import info.lwb.core.domain.UserSession

/**
 * Hilt DI module exposing user‑session related abstractions and bookmark use cases.
 *
 * Centralizes provisioning of a lightweight [UserSession] plus bookmark CRUD/search
 * operations so they can be injected across the app. Each provider returns a
 * focused use case constructed around the shared [BookmarkRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object UserSessionModule {
    /**
     * Provides a lightweight [UserSession] implementation backed by [AuthFacade].
     *
     * The returned session delegates to the authentication facade to resolve the
     * currently signed‑in user's unique identifier. A null value indicates that
     * there is no authenticated user.
     */
    @Provides
    fun provideUserSession(auth: AuthFacade): UserSession = object : UserSession {
        override fun currentUserId(): String? = auth.currentUser()?.uid
    }

    /**
     * Provides a use case that streams or retrieves the list of bookmarks for the active user.
     */
    @Provides fun provideGetBookmarks(repo: BookmarkRepository) = GetBookmarksUseCase(repo)

    /**
     * Provides a use case responsible for adding a bookmark to the user's collection.
     */
    @Provides fun provideAddBookmark(repo: BookmarkRepository) = AddBookmarkUseCase(repo)

    /**
     * Provides a use case that removes an existing bookmark identified by its id.
     */
    @Provides fun provideRemoveBookmark(repo: BookmarkRepository) = RemoveBookmarkUseCase(repo)

    /**
     * Provides a use case that returns the hierarchical folder structure for bookmarks.
     */
    @Provides fun provideGetBookmarkFolders(repo: BookmarkRepository) = GetBookmarkFoldersUseCase(repo)

    /**
     * Provides a use case that creates a new bookmark folder for organizational purposes.
     */
    @Provides fun provideCreateFolder(repo: BookmarkRepository) = CreateFolderUseCase(repo)

    /**
     * Provides a use case that moves a bookmark from one folder (or root) to another.
     */
    @Provides fun provideMoveBookmark(repo: BookmarkRepository) = MoveBookmarkUseCase(repo)

    /**
     * Provides a use case that performs a text search across the user's bookmarked content.
     */
    @Provides fun provideSearchBookmarked(repo: BookmarkRepository) = SearchBookmarkedUseCase(repo)
}
