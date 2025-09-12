/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthViewModelLoginNoRecaptchaTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun passwordLogin_doesNotRequireRecaptcha() = runTest {
        var calledWithToken: String? = "<not-called>"
        val facade = object : AuthFacade {
            override suspend fun oneTapSignIn(activity: android.app.Activity) =
                Result.failure<AuthUser>(UnsupportedOperationException())
            override fun currentUser(): AuthUser? = null
            override suspend fun signOut() { }
            override suspend fun refreshIdToken(forceRefresh: Boolean) =
                Result.failure<String>(UnsupportedOperationException())
            override suspend fun register(username: String, password: String, recaptchaToken: String?) =
                Result.failure<AuthUser>(UnsupportedOperationException())
            override suspend fun passwordLogin(
                username: String,
                password: String,
                recaptchaToken: String?,
            ): Result<AuthUser> {
                calledWithToken = recaptchaToken
                return Result.success(AuthUser(uid = "u1", displayName = username, email = null, photoUrl = null))
            }
        }
        var requestedAction: com.google.android.recaptcha.RecaptchaAction? = null
        val recaptcha = object : RecaptchaTokenProvider {
            override suspend fun getToken(action: com.google.android.recaptcha.RecaptchaAction): String? {
                requestedAction = action
                return "dummy"
            }
        }

        val vm = AuthViewModel(facade, recaptcha, dispatcher)
        vm.passwordLogin("user", "Password123!")
        dispatcher.scheduler.advanceUntilIdle()

        // Should not have requested any recaptcha token and must pass null to facade
        assertTrue("Recaptcha should not be requested for login", requestedAction == null)
        assertTrue("Facade should be called with null token", calledWithToken == null)
    }
}
