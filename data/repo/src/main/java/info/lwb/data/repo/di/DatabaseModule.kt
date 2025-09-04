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
import info.lwb.core.domain.ArticleRepository
import info.lwb.data.network.ArticleApi
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.repositories.ArticleRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lwb.db").build()

    @Provides fun provideArticleDao(db: AppDatabase): info.lwb.data.repo.db.ArticleDao = db.articleDao()

    @Provides fun provideBookmarkDao(db: AppDatabase): info.lwb.data.repo.db.BookmarkDao = db.bookmarkDao()

    @Provides fun provideFolderDao(db: AppDatabase): info.lwb.data.repo.db.FolderDao = db.folderDao()

    @Provides fun provideAnnotationDao(db: AppDatabase): info.lwb.data.repo.db.AnnotationDao = db.annotationDao()

    @Provides
    fun provideThreadMessageDao(db: AppDatabase): info.lwb.data.repo.db.ThreadMessageDao = db.threadMessageDao()

    @Provides
    @Singleton
    fun provideArticleRepository(api: ArticleApi, db: AppDatabase): ArticleRepository =
        ArticleRepositoryImpl(api, db.articleDao())
}
