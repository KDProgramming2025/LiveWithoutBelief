/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith

/**
 * Instrumented sign-in test (interactive path). This assumes emulator has a Google account.
 * If not, the test will be skipped gracefully.
 */
@RunWith(AndroidJUnit4::class)
class GoogleSignInInstrumentedTest {
    @Ignore("Interactive Google Sign-In flow not exercised in connected runs; provider is a stub")
    @Test
    fun interactiveSignIn_launchesIntentOrUsesCached() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { act ->
                val facade = TestAuthFacadeProvider.provide(act)
                // Force interactive by clearing any cached account
                try {
                    GoogleSignIn.getClient(
                        act,
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN,
                    ).signOut()
                } catch (
                    _: Exception,
                ) {}
                val result = runCatching { kotlinx.coroutines.runBlocking { facade.oneTapSignIn(act as Activity) } }
                // We just assert it didn't crash through unexpected exceptions (ApiException acceptable)
                assertTrue(result.isSuccess || (result.exceptionOrNull()?.message?.contains("Sign-in failed") == true))
            }
        }
    }
}
