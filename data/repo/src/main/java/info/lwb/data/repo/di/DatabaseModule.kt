package info.lwb.data.repo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.lwb.data.network.ArticleApi
import info.lwb.data.repo.db.AppDatabase
import info.lwb.data.repo.repositories.ArticleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lwb.db").build()

    @Provides fun provideArticleDao(db: AppDatabase) = db.articleDao()
    @Provides fun provideBookmarkDao(db: AppDatabase) = db.bookmarkDao()
    @Provides fun provideFolderDao(db: AppDatabase) = db.folderDao()
    @Provides fun provideAnnotationDao(db: AppDatabase) = db.annotationDao()
    @Provides fun provideThreadMessageDao(db: AppDatabase) = db.threadMessageDao()

    @Provides
    @Singleton
    fun provideArticleRepository(api: ArticleApi, db: AppDatabase): ArticleRepository = ArticleRepository(api, db.articleDao())
}
