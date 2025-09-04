/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase

@Module
@InstallIn(ViewModelComponent::class)
object ReaderModule {

    @Provides
    fun provideGetArticlesUseCase(articleRepository: ArticleRepository): GetArticlesUseCase {
        return GetArticlesUseCase(articleRepository)
    }

    @Provides
    fun provideGetArticleContentUseCase(articleRepository: ArticleRepository): GetArticleContentUseCase {
        return GetArticleContentUseCase(articleRepository)
    }

    @Provides
    fun provideRefreshArticlesUseCase(articleRepository: ArticleRepository): RefreshArticlesUseCase {
        return RefreshArticlesUseCase(articleRepository)
    }
}
