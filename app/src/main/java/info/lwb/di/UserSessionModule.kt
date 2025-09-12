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

@Module
@InstallIn(SingletonComponent::class)
object UserSessionModule {
    @Provides
    fun provideUserSession(auth: AuthFacade): UserSession = object : UserSession {
        override fun currentUserId(): String? = auth.currentUser()?.uid
    }

    @Provides fun provideGetBookmarks(repo: BookmarkRepository) = GetBookmarksUseCase(repo)

    @Provides fun provideAddBookmark(repo: BookmarkRepository) = AddBookmarkUseCase(repo)

    @Provides fun provideRemoveBookmark(repo: BookmarkRepository) = RemoveBookmarkUseCase(repo)

    @Provides fun provideGetBookmarkFolders(repo: BookmarkRepository) = GetBookmarkFoldersUseCase(repo)

    @Provides fun provideCreateFolder(repo: BookmarkRepository) = CreateFolderUseCase(repo)

    @Provides fun provideMoveBookmark(repo: BookmarkRepository) = MoveBookmarkUseCase(repo)

    @Provides fun provideSearchBookmarked(repo: BookmarkRepository) = SearchBookmarkedUseCase(repo)
}
