/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.lwb.auth.AuthFacade
import info.lwb.core.domain.UserSession

/**
 * Hilt DI module exposing user‑session related abstractions and bookmark use cases.
 *
 * Centralizes provisioning of a lightweight [UserSession] plus bookmark CRUD/search
 * operations so they can be injected across the app. Each provider returns a
 * focused use case constructed around the shared [BookmarkRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object UserSessionModule {
    /**
     * Provides a lightweight [UserSession] implementation backed by [AuthFacade].
     *
     * The returned session delegates to the authentication facade to resolve the
     * currently signed‑in user's unique identifier. A null value indicates that
     * there is no authenticated user.
     */
    @Provides
    fun provideUserSession(auth: AuthFacade): UserSession = object : UserSession {
        override fun currentUserId(): String? = auth.currentUser()?.uid
    }
}
