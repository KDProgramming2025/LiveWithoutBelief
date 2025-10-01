/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_MINUTE = 60L
private const val DEFAULT_REFRESH_LEAD_TIME_SECONDS = 5 * SECONDS_PER_MINUTE
private const val DEFAULT_POLL_INTERVAL_SECONDS = 60L
private const val MILLIS_PER_SECOND = 1000L

/**
 * Configuration for automatic token refresh.
 * @property refreshLeadTimeSeconds How many seconds before expiry a refresh should be attempted.
 * @property pollIntervalSeconds Interval between background polls.
 */
data class TokenRefreshConfig(
    val refreshLeadTimeSeconds: Long = DEFAULT_REFRESH_LEAD_TIME_SECONDS,
    val pollIntervalSeconds: Long = DEFAULT_POLL_INTERVAL_SECONDS,
)

/**
 * Automatically refreshes an ID token shortly before expiry.
 *
 * Call [start] once (idempotent) and later [stop] to cancel. Designed to be used as a singleton.
 * Thread-safety: confined to the provided [scope].
 */
class AutoTokenRefresher(
    private val storage: SecureStorage,
    private val authFacade: AuthFacade,
    private val sessionValidator: SessionValidator,
    private val scope: CoroutineScope,
    private val config: TokenRefreshConfig = TokenRefreshConfig(),
) {
    private var job: Job? = null

    /** Start background refresh loop if not already running. */
    fun start() {
        if (job != null) {
            return
        }
        job = scope.launch {
            var didInitialValidation = false
            while (isActive) {
                performIteration(::authFacadeRefresh, didInitialValidation).also { didInitialValidation = true }
                delay(config.pollIntervalSeconds.seconds)
            }
        }
    }

    /** Stop background refresh loop if running. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun performIteration(refresh: () -> Unit, didInitialValidation: Boolean): Boolean =
        try {
            if (!didInitialValidation) {
                validateExistingOnce()
            }
            maybeRefresh(refresh)
            true
        } catch (_: Exception) {
            // Swallow all exceptions – background best-effort policy.
            true
        }

    private suspend fun validateExistingOnce() {
        val existing = storage.getIdToken()
        if (!existing.isNullOrEmpty()) {
            runCatching { sessionValidator.validate(existing) }
        }
    }

    private suspend fun maybeRefresh(refresh: () -> Unit) {
        val expirySeconds = storage.getTokenExpiry() ?: return
        val nowSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND
        val remaining = expirySeconds - nowSeconds
        if (remaining <= config.refreshLeadTimeSeconds) {
            refresh()
        }
    }

    private fun authFacadeRefresh() = authFacade.refreshIdToken(false)
}
