/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.BookmarkRepository
import info.lwb.core.domain.ReadingProgressRepository
import info.lwb.data.network.ArticleApi
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.repositories.ArticleRepositoryImpl
import info.lwb.data.repo.repositories.BookmarkRepositoryImpl
import info.lwb.data.repo.repositories.ReadingProgressRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_progress (
                    articleId TEXT NOT NULL PRIMARY KEY,
                    pageIndex INTEGER NOT NULL,
                    totalPages INTEGER NOT NULL,
                    progress REAL NOT NULL,
                    updatedAt TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lwb.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideArticleDao(db: AppDatabase): info.lwb.data.repo.db.ArticleDao = db.articleDao()

    @Provides fun provideBookmarkDao(db: AppDatabase): info.lwb.data.repo.db.BookmarkDao = db.bookmarkDao()

    @Provides fun provideFolderDao(db: AppDatabase): info.lwb.data.repo.db.FolderDao = db.folderDao()

    @Provides fun provideAnnotationDao(db: AppDatabase): info.lwb.data.repo.db.AnnotationDao = db.annotationDao()

    @Provides
    fun provideThreadMessageDao(db: AppDatabase): info.lwb.data.repo.db.ThreadMessageDao = db.threadMessageDao()

    @Provides
    fun provideReadingProgressDao(db: AppDatabase): info.lwb.data.repo.db.ReadingProgressDao = db.readingProgressDao()

    @Provides
    @Singleton
    fun provideArticleRepository(api: ArticleApi, db: AppDatabase): ArticleRepository =
        ArticleRepositoryImpl(api, db.articleDao())

    @Provides
    fun provideReadingProgressRepository(db: AppDatabase): ReadingProgressRepository =
        ReadingProgressRepositoryImpl(db.readingProgressDao())

    @Provides
    fun provideBookmarkRepository(db: AppDatabase, session: info.lwb.core.domain.UserSession): BookmarkRepository =
        BookmarkRepositoryImpl(db.bookmarkDao(), db.folderDao(), session)
}
