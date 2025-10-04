/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.util.Log
import info.lwb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBUG_TAG = "AuthValidate"
private const val AUTH_VALIDATE_PATH = "/v1/auth/google"
private const val RETRY_AFTER = "Retry-After"
private const val JSON = "application/json"
private const val HTTP_OK = 200
private const val HTTP_CREATED = 201
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_SERVER_MIN = 500
private const val HTTP_SERVER_MAX = 599

/**
 * Retry configuration governing exponential backoff for remote session validation.
 * @property maxAttempts Total validation attempts (must be >= 1).
 * @property baseDelayMs Base delay for the second attempt (first retry) in milliseconds.
 * @property backoffMultiplier Multiplier applied exponentially (attempt-1) to the base delay.
 */
@Singleton
data class ValidationRetryPolicy(
    /** Total validation attempts (including the first). */
    val maxAttempts: Int = 3,
    /** Base delay for second attempt in milliseconds (subsequent attempts are multiplied). */
    val baseDelayMs: Long = 50,
    /** Exponential multiplier for each retry after the first failed attempt. */
    val backoffMultiplier: Double = 2.0,
)

/** Lightweight hook for observability (can be bridged to Timber/Logcat or metrics). */
interface ValidationObserver {
    /** Called before each attempt (1-based index). */
    fun onAttempt(attempt: Int, max: Int)

    /** Called after each attempt with the resulting [ValidationResult]. */
    fun onResult(result: ValidationResult)

    /** Called prior to delaying for a retry with the computed delay milliseconds. */
    fun onRetry(delayMs: Long)
}

/** No-op observer used by default. */
object NoopValidationObserver : ValidationObserver {
    override fun onAttempt(attempt: Int, max: Int) = Unit

    override fun onResult(result: ValidationResult) = Unit

    override fun onRetry(delayMs: Long) = Unit
}

/** Persistence abstraction tracking locally revoked tokens to avoid network calls for known-invalid tokens. */
interface RevocationStore {
    /** Marks the supplied token as revoked locally. */
    fun markRevoked(token: String)

    /** Returns true if token was locally marked revoked. */
    fun isRevoked(token: String): Boolean
}

/** In-memory implementation suitable for tests or ephemeral processes. */
class InMemoryRevocationStore @Inject constructor() : RevocationStore {
    private val revoked = ConcurrentHashMap.newKeySet<String>()

    override fun markRevoked(token: String) {
        revoked.add(token)
    }

    override fun isRevoked(token: String): Boolean = token in revoked
}

/** Simple persistent implementation backed by encrypted shared preferences (same security profile as SecureStorage). */
@Singleton
class PrefsRevocationStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context,
) : RevocationStore {
    private val prefs by lazy {
        // Use normal (not encrypted) SharedPreferences because tokens are already short-lived; however
        // if stricter requirements arise we can switch to EncryptedSharedPreferences similarly to SecureStorage.
        // For now keep lightweight to avoid double encryption overhead.
        appContext.getSharedPreferences("revocations.store", android.content.Context.MODE_PRIVATE)
    }

    // Key format: token hash -> 1 (value unused). Store only SHA-256 to avoid persisting raw tokens.
    private fun hash(token: String): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        token // fall back to raw (rare edge case)
    }

    override fun markRevoked(token: String) {
        val h = hash(token)
        prefs.edit().putBoolean(h, true).apply()
    }

    override fun isRevoked(token: String): Boolean = prefs.getBoolean(hash(token), false)
}

/** No-op revocation store for cases where we do not persist local revocations. */
object NoopRevocationStore : RevocationStore {
    override fun markRevoked(token: String) = Unit

    override fun isRevoked(token: String) = false
}

/**
 * Validates Google ID tokens against the backend auth endpoint with retries & local revocation caching.
 * Behavior:
 *  - Short-circuits immediately when token is locally revoked.
 *  - Retries transient network/server (5xx with Retry-After) failures using exponential backoff.
 *  - Treats 400/401 uniformly as Unauthorized for simplified UX handling.
 */
class RemoteSessionValidator @Inject constructor(
    @AuthClient private val client: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
    private val retryPolicy: ValidationRetryPolicy,
    private val observer: ValidationObserver = NoopValidationObserver,
    private val revocationStore: RevocationStore = NoopRevocationStore,
) : SessionValidator {
    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path

    override suspend fun validate(idToken: String): Boolean = validateDetailed(idToken).isValid

    override suspend fun validateDetailed(idToken: String): ValidationResult = withContext(Dispatchers.IO) {
        if (revocationStore.isRevoked(idToken)) {
            ValidationResult(false, ValidationError.Unauthorized)
        } else {
            performValidation(idToken)
        }
    }

    private suspend fun performValidation(idToken: String): ValidationResult {
        val maxAttempts = retryPolicy
            .maxAttempts
            .coerceAtLeast(1)
        var attempt = 0
        var lastServerRetryable = false
        var last: ValidationResult
        while (true) {
            observer.onAttempt(attempt + 1, maxAttempts)
            val request = buildRequest(idToken)
            val result = execute(request) { code, retryAfter ->
                lastServerRetryable = retryAfter
                evaluateStatus(code)
            }
            last = result
            observer.onResult(result)
            val retryable = isRetryable(result, lastServerRetryable)
            if (result.isValid || !retryable || attempt == maxAttempts - 1) {
                break
            }
            attempt++
            val delayMs = computeDelay(attempt)
            if (delayMs > 0) {
                observer.onRetry(delayMs)
                delay(delayMs)
            }
        }
        return last
    }

    private fun buildRequest(idToken: String): Request {
        val path = AUTH_VALIDATE_PATH
        val body = "{\"idToken\":\"$idToken\"}"
        debugLog("request url=${endpoint(path)} bodyLen=${body.length}")
        return Request
            .Builder()
            .url(endpoint(path))
            .post(body.toRequestBody(JSON.toMediaType()))
            .build()
    }

    private inline fun execute(
        request: Request,
        map: (code: Int, retryAfterHeaderPresent: Boolean) -> ValidationResult,
    ): ValidationResult = try {
        client
            .newCall(request)
            .execute()
            .use { r ->
                val retryAfterValue = r.header(RETRY_AFTER) ?: "<none>"
                debugLog("response code=${r.code} retryAfter=$retryAfterValue")
                val retryableHeader = r.header(RETRY_AFTER) != null
                map(r.code, retryableHeader)
            }
    } catch (_: Exception) {
        ValidationResult(false, ValidationError.Network)
    }

    private fun evaluateStatus(code: Int): ValidationResult = when {
        code == HTTP_OK || code == HTTP_CREATED -> {
            ValidationResult(true, null)
        }
        code == HTTP_BAD_REQUEST || code == HTTP_UNAUTHORIZED -> {
            ValidationResult(false, ValidationError.Unauthorized)
        }
        code in HTTP_SERVER_MIN..HTTP_SERVER_MAX -> {
            ValidationResult(false, ValidationError.Server(code))
        }
        else -> {
            ValidationResult(false, ValidationError.Unexpected("HTTP $code"))
        }
    }

    private fun isRetryable(result: ValidationResult, serverRetryable: Boolean): Boolean = when (result.error) {
        ValidationError.Network -> {
            true
        }
        is ValidationError.Server -> {
            serverRetryable
        }
        else -> {
            false
        }
    }

    private fun computeDelay(attempt: Int): Long = if (attempt == 0) {
        0L
    } else {
        val factor = Math
            .pow(retryPolicy.backoffMultiplier, (attempt - 1).toDouble())
        (retryPolicy.baseDelayMs * factor).toLong()
    }

    private fun debugLog(message: String) = if (BuildConfig.DEBUG) {
        runCatching {
            Log
                .d(DEBUG_TAG, message)
        }
    } else {
        Unit
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) { /* No-op (natural expiry). */ }
}
