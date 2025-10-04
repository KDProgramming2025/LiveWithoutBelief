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

/**
 * Hilt module exposing reader feature use cases to ViewModel scoped components.
 * Keeps construction logic centralized and test-friendly.
 */
@Module
@InstallIn(ViewModelComponent::class)
object ReaderModule {
    // Article listing and content use cases now provided by feature:articles ArticlesModule.
    // Only keep reader-specific provisioning below.

    /** Provides a use case to create and persist a new annotation for an article. */
    @Provides
    fun provideAddAnnotationUseCase(annotationRepository: AnnotationRepository): AddAnnotationUseCase =
        AddAnnotationUseCase(annotationRepository = annotationRepository)
}
