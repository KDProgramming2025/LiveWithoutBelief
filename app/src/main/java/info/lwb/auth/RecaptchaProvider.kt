/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

/**
 * Legacy reCAPTCHA shim. This project migrated to ALTCHA and no longer uses reCAPTCHA.
 * We keep minimal stubs so that any lingering imports compile while sources are cleaned up.
 */
@Deprecated("Replaced by ALTCHA; do not use")
interface RecaptchaTokenProvider {
    suspend fun getToken(): String? = null
}

@Deprecated("Replaced by ALTCHA; do not use")
class CachingRecaptchaProvider(
    private val delegate: RecaptchaTokenProvider,
    private val ttlMillis: Long = 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) : RecaptchaTokenProvider {
    @Volatile private var cached: String? = null
    @Volatile private var expiry: Long = 0L
    override suspend fun getToken(): String? {
        val current = now()
        val existing = cached
        if (existing != null && current < expiry) return existing
        val fresh = delegate.getToken()
        if (fresh != null) {
            cached = fresh
            expiry = current + ttlMillis
        }
        return fresh
    }
}

@Deprecated("Replaced by ALTCHA; do not use")
class GoogleRecaptchaTokenProvider : RecaptchaTokenProvider
