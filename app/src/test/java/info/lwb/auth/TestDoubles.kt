/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse

/** Lightweight test-only SecureStorage that keeps values in memory. */
class InMemorySecureStorage : SecureStorage {
    private var token: String? = null
    private var exp: Long? = null
    private var name: String? = null
    private var email: String? = null
    private var avatar: String? = null

    override fun putIdToken(token: String) {
        this.token = token
    }

    override fun getIdToken(): String? = token

    override fun putTokenExpiry(epochSeconds: Long?) {
        exp = epochSeconds
    }

    override fun getTokenExpiry(): Long? = exp

    override fun clear() {
        token = null
        exp = null
        name = null
        email = null
        avatar = null
    }

    override fun putProfile(name: String?, email: String?, avatar: String?) {
        this.name = name
        this.email = email
        this.avatar = avatar
    }

    override fun getProfile(): Triple<String?, String?, String?> = Triple(name, email, avatar)
}

/** Simple interface to abstract the Credentials API call for testability. */
interface CredentialCall {
    suspend fun get(
        activity: Activity,
        request: GetCredentialRequest,
    ): GetCredentialResponse
}

/** Minimal provider for tests that attempts to call the CredentialCall and extracts a token when possible. */
class CredentialManagerOneTapProvider(private val call: CredentialCall) {
    suspend fun getIdToken(activity: Activity): String? = try {
    val req = GetCredentialRequest(credentialOptions = emptyList())
        call.get(activity, req)
        // In real impl we'd inspect resp.credential. For tests, return null (no crash path)
        null
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }
}
