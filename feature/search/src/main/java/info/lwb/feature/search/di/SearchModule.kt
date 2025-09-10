/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.search.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.domain.SearchArticlesUseCase

@Module
@InstallIn(ViewModelComponent::class)
object SearchModule {
    @Provides
    fun provideSearchArticlesUseCase(repo: ArticleRepository): SearchArticlesUseCase =
        SearchArticlesUseCase(repo)
}
