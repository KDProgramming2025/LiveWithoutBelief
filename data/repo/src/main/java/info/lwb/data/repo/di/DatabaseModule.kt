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
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.BookmarkRepository
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.domain.GetMenuUseCase
import info.lwb.core.domain.LabelArticleRepository
import info.lwb.core.domain.MenuRepository
import info.lwb.core.domain.ReadingProgressRepository
import info.lwb.core.domain.RefreshMenuUseCase
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.MenuApi
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.repositories.AnnotationRepositoryImpl
import info.lwb.data.repo.repositories.ArticleRepositoryImpl
import info.lwb.data.repo.repositories.BookmarkRepositoryImpl
import info.lwb.data.repo.repositories.LabelArticleRepositoryImpl
import info.lwb.data.repo.repositories.ReadingProgressRepositoryImpl
import info.lwb.data.repo.repositories.menu.MenuRepositoryImpl
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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add nullable indexUrl column to article_contents
            db.execSQL("ALTER TABLE article_contents ADD COLUMN indexUrl TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lwb.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    @Singleton
    fun provideLabelArticleRepository(api: ArticleApi, db: AppDatabase): LabelArticleRepository =
        LabelArticleRepositoryImpl(api, db.articleDao())

    @Provides
    fun provideReadingProgressRepository(db: AppDatabase): ReadingProgressRepository =
        ReadingProgressRepositoryImpl(db.readingProgressDao())

    @Provides
    fun provideBookmarkRepository(db: AppDatabase, session: info.lwb.core.domain.UserSession): BookmarkRepository =
        BookmarkRepositoryImpl(db.bookmarkDao(), db.folderDao(), session)

    @Provides
    fun provideAnnotationRepository(db: AppDatabase, session: info.lwb.core.domain.UserSession): AnnotationRepository =
        AnnotationRepositoryImpl(db.annotationDao(), db.threadMessageDao(), session)

    @Provides
    @Singleton
    fun provideMenuRepository(api: MenuApi): MenuRepository = MenuRepositoryImpl(api)

    @Provides fun provideGetMenuUseCase(repo: MenuRepository) = GetMenuUseCase(repo)

    @Provides fun provideRefreshMenuUseCase(repo: MenuRepository) = RefreshMenuUseCase(repo)

    @Provides fun provideGetArticlesByLabelUseCase(repo: LabelArticleRepository) = GetArticlesByLabelUseCase(repo)
}
