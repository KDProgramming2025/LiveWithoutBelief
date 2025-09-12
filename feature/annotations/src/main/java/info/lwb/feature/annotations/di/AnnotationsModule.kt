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

@Module
@InstallIn(ViewModelComponent::class)
object AnnotationsModule {
    @Provides
    fun provideGetThreadMessages(repo: AnnotationRepository): GetThreadMessagesUseCase = GetThreadMessagesUseCase(repo)

    @Provides
    fun provideAddThreadMessage(repo: AnnotationRepository): AddThreadMessageUseCase = AddThreadMessageUseCase(repo)
}
