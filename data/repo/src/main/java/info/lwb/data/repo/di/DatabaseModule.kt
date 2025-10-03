/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.di

import android.content.Context
import androidx.room.Room
// Removed migration imports as they are no longer needed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.BookmarkRepository
import info.lwb.core.domain.GetMenuUseCase
import info.lwb.core.domain.MenuRepository
import info.lwb.core.domain.ReadingProgressRepository
import info.lwb.core.domain.RefreshMenuUseCase
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.MenuApi
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.repositories.AnnotationRepositoryImpl
import info.lwb.data.repo.repositories.ArticleRepositoryImpl
import info.lwb.data.repo.repositories.BookmarkRepositoryImpl
import info.lwb.data.repo.repositories.ReadingProgressRepositoryImpl
import info.lwb.data.repo.repositories.menu.MenuRepositoryImpl
import info.lwb.data.repo.db.MenuDao
import javax.inject.Singleton

/**
 * Hilt module wiring Room database, migrations, DAOs, repositories and related use cases.
 * Each provider supplies a singleton or factory for data layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /** Provide the Room [AppDatabase] instance with registered migrations. */
    @Provides
    @Singleton
    fun provideDb(
        @ApplicationContext context: Context,
    ): AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, DB_NAME)
        .fallbackToDestructiveMigration()
        .build()

    /** DAO for articles table. */
    @Provides
    fun provideArticleDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.ArticleDao = db.articleDao()

    /** DAO for bookmarks table. */
    @Provides
    fun provideBookmarkDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.BookmarkDao = db.bookmarkDao()

    /** DAO for folders table. */
    @Provides
    fun provideFolderDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.FolderDao = db.folderDao()

    /** DAO for annotations table. */
    @Provides
    fun provideAnnotationDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.AnnotationDao = db.annotationDao()

    /** DAO for thread messages. */
    @Provides
    fun provideThreadMessageDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.ThreadMessageDao = db.threadMessageDao()

    /** DAO for reading progress entries. */
    @Provides
    fun provideReadingProgressDao(
        db: AppDatabase,
    ): info.lwb.data.repo.db.ReadingProgressDao = db.readingProgressDao()

    /** DAO for menu items table. */
    @Provides
    fun provideMenuDao(
        db: AppDatabase,
    ): MenuDao = db.menuDao()

    /** Repository for articles syncing and persistence. */
    @Provides
    @Singleton
    fun provideArticleRepository(
        api: ArticleApi,
        db: AppDatabase,
    ): ArticleRepository = ArticleRepositoryImpl(
        api = api,
        articleDao = db.articleDao(),
    )

    /** Repository for reading progress persistence. */
    @Provides
    fun provideReadingProgressRepository(
        db: AppDatabase,
    ): ReadingProgressRepository = ReadingProgressRepositoryImpl(
        db.readingProgressDao(),
    )

    /** Repository for bookmarks and folders. */
    @Provides
    fun provideBookmarkRepository(
        db: AppDatabase,
        session: info.lwb.core.domain.UserSession,
    ): BookmarkRepository = BookmarkRepositoryImpl(
        db.bookmarkDao(),
        db.folderDao(),
        session,
    )

    /** Repository for annotations and their thread messages. */
    @Provides
    fun provideAnnotationRepository(
        db: AppDatabase,
        session: info.lwb.core.domain.UserSession,
    ): AnnotationRepository = AnnotationRepositoryImpl(
        db.annotationDao(),
        db.threadMessageDao(),
        session,
    )

    /** Repository for menu structure retrieval. */
    @Provides
    @Singleton
    fun provideMenuRepository(
        api: MenuApi,
        menuDao: MenuDao,
    ): MenuRepository = MenuRepositoryImpl(api, menuDao)

    /** Use case to retrieve menu. */
    @Provides
    fun provideGetMenuUseCase(
        repo: MenuRepository,
    ): GetMenuUseCase = GetMenuUseCase(repo)

    /** Use case to refresh menu from remote. */
    @Provides
    fun provideRefreshMenuUseCase(
        repo: MenuRepository,
    ): RefreshMenuUseCase = RefreshMenuUseCase(repo)

    // Label filtering now handled by unified ArticleRepository; dedicated provider removed.

    private const val DB_NAME = "lwb.db"
}
