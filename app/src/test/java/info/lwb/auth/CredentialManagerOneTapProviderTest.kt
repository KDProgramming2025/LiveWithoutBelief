/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Basic sanity test: provider should return null (no crash) when run in a plain Activity
 * under Robolectric without real Credential Manager interaction.
 */
class CredentialManagerOneTapProviderTest {
    @Test
    fun returnsNullGracefullyWithoutServices() = runTest {
        val fakeCall = object : CredentialCall {
            override suspend fun get(
                activity: Activity,
                request: androidx.credentials.GetCredentialRequest,
            ): androidx.credentials.GetCredentialResponse {
                throw UnsupportedOperationException("fake")
            }
        }
        val provider = CredentialManagerOneTapProvider(fakeCall)
        val activity = Activity() // Robolectric plain activity
        val token = provider.getIdToken(activity)
        assertNull(token)
    }
}
