/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity

/**
 * Minimal, Hilt-free provider used only by legacy/ignored tests.
 * Returns a stub [AuthFacade] to avoid depending on removed auth wiring in androidTest.
 */
object TestAuthFacadeProvider {
    fun provide(@Suppress("UnusedParameter") activity: Activity): AuthFacade = object : AuthFacade {
        override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> =
            Result.failure(UnsupportedOperationException("oneTapSignIn not available in androidTest stub"))

        override fun currentUser(): AuthUser? = null

        override suspend fun signOut() {
            // no-op
        }

        override suspend fun refreshIdToken(forceRefresh: Boolean): Result<String> =
            Result.failure(UnsupportedOperationException("refreshIdToken not available in androidTest stub"))

        override suspend fun register(username: String, password: String, altchaPayload: String?): Result<AuthUser> =
            Result.failure(UnsupportedOperationException("register not available in androidTest stub"))

        override suspend fun passwordLogin(username: String, password: String): Result<AuthUser> =
            Result.failure(UnsupportedOperationException("passwordLogin not available in androidTest stub"))
    }
}
