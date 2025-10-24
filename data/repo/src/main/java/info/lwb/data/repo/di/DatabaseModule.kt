/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.UserSession
import info.lwb.data.network.ArticleApi
import info.lwb.data.repo.db.AnnotationDao
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ThreadMessageDao
import info.lwb.data.repo.repositories.AnnotationRepositoryImpl
import info.lwb.data.repo.repositories.ArticleRepositoryImpl
import javax.inject.Singleton

/**
 * Hilt module wiring Room database, DAOs, repositories and a few domain use cases.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "lwb.db"

    /** Provide the Room [AppDatabase] instance with registered migrations. */
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, DB_NAME)
        .fallbackToDestructiveMigration()
        .build()

    /** DAO for articles table. */
    @Provides
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()

    /** DAO for annotations table. */
    @Provides
    fun provideAnnotationDao(db: AppDatabase): AnnotationDao = db.annotationDao()

    /** DAO for thread messages. */
    @Provides
    fun provideThreadMessageDao(db: AppDatabase): ThreadMessageDao = db.threadMessageDao()

    /** DAO for menu items table. */
    @Provides
    fun provideMenuDao(db: AppDatabase): info.lwb.data.repo.db.MenuDao = db.menuDao()

    /**
     * Repository for articles syncing and persistence.
     */
    @Provides
    @Singleton
    fun provideArticleRepository(api: ArticleApi, db: AppDatabase): ArticleRepository =
        ArticleRepositoryImpl(
            api = api,
            articleDao = db.articleDao(),
        )

    /**
     * Repository for annotations and their thread messages.
     */
    @Provides
    fun provideAnnotationRepository(db: AppDatabase, session: UserSession): AnnotationRepository =
        AnnotationRepositoryImpl(
            db.annotationDao(),
            db.threadMessageDao(),
            session,
        )

    // Menu repository and use-cases moved to MenuDataModule for easier test replacement
}
