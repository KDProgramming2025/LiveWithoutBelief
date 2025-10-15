/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.annotations.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import info.lwb.core.domain.AddThreadMessageUseCase
import info.lwb.core.domain.AnnotationRepository
import info.lwb.core.domain.GetThreadMessagesUseCase

/**
 * Hilt module providing use case instances related to annotation discussion threads.
 *
 * These providers adapt the [AnnotationRepository] into higher-level intent specific
 * use case abstractions consumed by presentation layer view models.
 */
@Module
@InstallIn(ViewModelComponent::class)
object AnnotationsModule {
    /** Supplies a use case for retrieving all messages in a particular annotation thread. */
    @Provides
    fun provideGetThreadMessages(repo: AnnotationRepository): GetThreadMessagesUseCase = GetThreadMessagesUseCase(repo)

    /** Supplies a use case for appending a new message to an annotation discussion thread. */
    @Provides
    fun provideAddThreadMessage(repo: AnnotationRepository): AddThreadMessageUseCase = AddThreadMessageUseCase(repo)
}
