/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.articles.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase

/** Hilt module for Articles feature use case provisioning. */
@Module
@InstallIn(ViewModelComponent::class)
object ArticlesModule {
    /** Provides a use case streaming all articles. */
    @Provides
    fun provideGetArticlesUseCase(
        repo: ArticleRepository,
    ): GetArticlesUseCase = GetArticlesUseCase(
        articleRepository = repo,
    )

    /** Provides a use case returning articles filtered by label. */
    @Provides
    fun provideGetArticlesByLabelUseCase(
        repo: ArticleRepository,
    ): GetArticlesByLabelUseCase = GetArticlesByLabelUseCase(
        articleRepository = repo,
    )

    /** Provides a use case performing a refresh sync for articles. */
    @Provides
    fun provideRefreshArticlesUseCase(
        repo: ArticleRepository,
    ): RefreshArticlesUseCase = RefreshArticlesUseCase(
        articleRepository = repo,
    )
}
