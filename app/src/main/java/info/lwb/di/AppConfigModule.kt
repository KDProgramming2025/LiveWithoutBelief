/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = info.lwb.BuildConfig.API_BASE_URL
}
