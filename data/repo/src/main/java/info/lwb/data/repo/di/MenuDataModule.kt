/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.lwb.core.domain.GetMenuUseCase
import info.lwb.core.domain.MenuRepository
import info.lwb.core.domain.RefreshMenuUseCase
import info.lwb.data.network.MenuApi
import info.lwb.data.repo.db.MenuDao
import info.lwb.data.repo.repositories.menu.MenuRepositoryImpl
import javax.inject.Singleton

/**
 * Hilt module that wires menu-related data providers for production builds.
 * Provides [MenuRepository] along with [GetMenuUseCase] and [RefreshMenuUseCase].
 */
@Module
@InstallIn(SingletonComponent::class)
object MenuDataModule {
    /** Repository for menu structure retrieval. */
    @Provides
    @Singleton
    fun provideMenuRepository(api: MenuApi, menuDao: MenuDao): MenuRepository =
        MenuRepositoryImpl(api, menuDao)

    /** Use case for reactive menu retrieval. */
    @Provides
    fun provideGetMenuUseCase(repo: MenuRepository): GetMenuUseCase = GetMenuUseCase(repo)

    /** Use case to trigger remote menu refresh. */
    @Provides
    fun provideRefreshMenuUseCase(repo: MenuRepository): RefreshMenuUseCase = RefreshMenuUseCase(repo)
}
