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
        val recaptcha: RecaptchaTokenProvider = mockk()
        val vm = AuthViewModel(facade, recaptcha, mainDispatcherRule.dispatcher)
        vm.signIn { mockk<Activity>(relaxed = true) }
        runCurrent()
        val state = vm.state.first { it is AuthUiState.SignedIn }
        assertTrue(state is AuthUiState.SignedIn)
    }

    @Test
    fun passwordRegisterPassesRecaptchaToken() = runTest {
        val recaptcha: RecaptchaTokenProvider = mockk()
        coEvery { recaptcha.getToken(any()) } returns "r-token"
        every { facade.currentUser() } returns null
        coEvery { facade.register("e", "p", "r-token") } returns Result.success(AuthUser("u", "n", "e", null))
        val vm = AuthViewModel(facade, recaptcha, mainDispatcherRule.dispatcher)
        vm.passwordRegister("e", "p")
        runCurrent()
        coVerify { facade.register("e", "p", "r-token") }
        val state = vm.state.value
        assertTrue(state is AuthUiState.SignedIn)
    }

    @Test
    fun passwordRegisterRecaptchaFailureShowsError() = runTest {
        val recaptcha: RecaptchaTokenProvider = mockk()
        coEvery { recaptcha.getToken(any()) } returns null
        every { facade.currentUser() } returns null
        // facade.register should not be called
        val vm = AuthViewModel(facade, recaptcha, mainDispatcherRule.dispatcher)
        vm.passwordRegister("e", "p")
        runCurrent()
        coVerify(exactly = 0) { facade.register(any(), any(), any()) }
        val state = vm.state.value
        assertTrue(state is AuthUiState.Error)
    }
}
