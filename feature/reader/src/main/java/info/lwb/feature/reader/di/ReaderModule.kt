/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.AddAnnotationUseCase
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase

/**
 * Hilt module exposing reader feature use cases to ViewModel scoped components.
 * Keeps construction logic centralized and test-friendly.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ReaderModule {
    /** Provides use cases for reader-related domain operations. */
    @Provides
    fun provideGetArticlesUseCase(articleRepository: ArticleRepository): GetArticlesUseCase =
        GetArticlesUseCase(articleRepository = articleRepository)

    /** Provides a use case to retrieve a filtered snapshot of articles by label. */
    @Provides
    fun provideGetArticlesByLabelUseCase(articleRepository: ArticleRepository): GetArticlesByLabelUseCase =
        GetArticlesByLabelUseCase(articleRepository = articleRepository)

    /** Provides a use case to load article HTML/content payload. */
    @Provides
    fun provideGetArticleContentUseCase(articleRepository: ArticleRepository): GetArticleContentUseCase =
        GetArticleContentUseCase(articleRepository = articleRepository)

    /** Provides a use case to refresh remote + cache article list. */
    @Provides
    fun provideRefreshArticlesUseCase(articleRepository: ArticleRepository): RefreshArticlesUseCase =
        RefreshArticlesUseCase(articleRepository = articleRepository)

    /** Provides a use case to create and persist a new annotation for an article. */
    @Provides
    fun provideAddAnnotationUseCase(annotationRepository: AnnotationRepository): AddAnnotationUseCase =
        AddAnnotationUseCase(annotationRepository = annotationRepository)
}
