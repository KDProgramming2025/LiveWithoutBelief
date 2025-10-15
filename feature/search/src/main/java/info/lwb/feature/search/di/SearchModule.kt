/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.search.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.SearchArticlesUseCase

/**
 * Hilt module wiring search related domain dependencies into the ViewModel scope.
 *
 * Provides:
 *  - [SearchArticlesUseCase]: orchestrates article search logic via [ArticleRepository].
 */
@Module
@InstallIn(ViewModelComponent::class)
object SearchModule {
    /** Supplies a [SearchArticlesUseCase] backed by the injected [ArticleRepository]. */
    @Provides
    fun provideSearchArticlesUseCase(repo: ArticleRepository): SearchArticlesUseCase = SearchArticlesUseCase(repo)
}
