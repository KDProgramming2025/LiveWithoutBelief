/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import info.lwb.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val facade: AuthFacade = mockk()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun signInSuccessUpdatesState() = runTest {
        val user = AuthUser("id", "name", "email", null)
        coEvery { facade.oneTapSignIn(any()) } returns Result.success(user)
        every { facade.currentUser() } returns null
        val vm = AuthViewModel(facade, null)
        vm.signIn { mockk<Activity>(relaxed = true) }
        runCurrent()
        val state = vm.state.first { it is AuthUiState.SignedIn }
        assertTrue(state is AuthUiState.SignedIn)
    }

    @Test
    fun passwordRegisterPassesAltchaToken() = runTest {
        // Provide a fake ALTCHA provider returning a token
        val altcha = object : AltchaTokenProvider {
            override suspend fun solve(activity: Activity): String? = "altcha-token"
        }
        every { facade.currentUser() } returns null
        coEvery { facade.register("e", "p", "altcha-token") } returns Result.success(AuthUser("u", "n", "e", null))
        val vm = AuthViewModel(facade, altcha)
        // Simulate direct facade call path (unit scope)
        vm.passwordRegister({ mockk<Activity>(relaxed = true) }, "e", "p")
        runCurrent()
        coVerify { facade.register("e", "p", "altcha-token") }
        val state = vm.state.value
        assertTrue(state is AuthUiState.SignedIn)
    }

    @Test
    fun passwordRegisterAltchaFailureShowsError() = runTest {
        // Simulate ALTCHA failure by making facade.register never called due to null token path in production code
        every { facade.currentUser() } returns null
        // Simulate ALTCHA failure by providing a provider that returns null
        val altchaFail = object : AltchaTokenProvider {
            override suspend fun solve(activity: Activity): String? = null
        }
        val vm = AuthViewModel(facade, altchaFail)
        vm.passwordRegister({ mockk<Activity>(relaxed = true) }, "e", "p")
        runCurrent()
        coVerify(exactly = 0) { facade.register(any(), any(), any()) }
        val state = vm.state.value
        assertTrue(state is AuthUiState.Error)
    }
}
