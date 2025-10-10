/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module exposing application configuration constants that originate from build time
 * generated values (Gradle buildConfigField). Centralising these providers allows a single
 * injection source for base URLs and future environment-scoped config values.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    /**
     * Provides the base URL for the public API used by network data sources.
     * The value is sourced from the generated [info.lwb.BuildConfig] to allow
     * per-build-type / per-flavor overrides.
     */
    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = requireNotNull(info.lwb.BuildConfig.API_BASE_URL) {
        "API_BASE_URL is not configured. Set APP_SERVER_HOST (and optionally APP_SERVER_SCHEME) " +
            "or provide API_BASE_URL via environment/Gradle property."
    }

    /**
     * Provides the base URL used for retrieving user-uploaded (or static media) assets.
     * Kept separate from the API base to support distinct CDN domains or caching policies.
     */
    @Provides
    @Singleton
    @Named("uploadsBaseUrl")
    fun provideUploadsBaseUrl(): String = requireNotNull(info.lwb.BuildConfig.UPLOADS_BASE_URL) {
        "UPLOADS_BASE_URL is not configured. Set APP_SERVER_HOST (and optionally APP_SERVER_SCHEME) " +
            "or provide UPLOADS_BASE_URL via environment/Gradle property."
    }
}
