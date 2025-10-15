/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a successful Google Identity one-tap / credential manager sign-in result.
 *
 * @property idToken Raw Google ID token (JWT) issued for the configured server client id.
 * @property displayName User's display name if provided by the identity provider (nullable).
 * @property email The unique user identifier (email address). Note: the GoogleIdTokenCredential exposes the
 *                 subject via [GoogleIdTokenCredential.getId]; that value is mapped here to [email] for legacy
 *                 downstream usage expecting an e-mail identifier.
 */
data class GoogleIdTokenResult(val idToken: String, val displayName: String?, val email: String?)

/**
 * Facade abstraction encapsulating Google Identity credential acquisition so that higher-level
 * authentication logic does not depend directly on the Credential Manager APIs (improves testability
 * and isolates future provider changes).
 */
interface IdentityCredentialFacade {
    /**
     * Launches a credential retrieval flow (may display UI depending on auto-select eligibility) and returns
     * the resulting [GoogleIdTokenResult] on success. Errors are surfaced via thrown exceptions which callers
     * can map to user-visible states.
     */
    suspend fun getIdToken(activity: ComponentActivity): GoogleIdTokenResult
}

/**
 * Default production implementation backed by the AndroidX Credential Manager + Google Identity Services.
 * Runs work on [Dispatchers.IO] to avoid blocking the main thread during credential resolution.
 */
class DefaultIdentityCredentialFacade(@ServerClientId private val serverClientId: String) : IdentityCredentialFacade {
    override suspend fun getIdToken(activity: ComponentActivity): GoogleIdTokenResult = withContext(Dispatchers.IO) {
        val cm = CredentialManager.create(activity)
        val googleOption =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .build()
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(googleOption)
                .build()
        val response: GetCredentialResponse = cm.getCredential(activity, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        GoogleIdTokenResult(
            idToken = credential.idToken,
            displayName = credential.displayName,
            email = credential.id,
        )
    }
}
