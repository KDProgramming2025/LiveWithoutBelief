/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import info.lwb.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class ValidationRetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 50,
    val backoffMultiplier: Double = 2.0,
)

/** Lightweight hook for observability (can be bridged to Timber/Logcat or metrics). */
interface ValidationObserver {
    fun onAttempt(attempt: Int, max: Int)
    fun onResult(result: ValidationResult)
    fun onRetry(delayMs: Long)
}

object NoopValidationObserver : ValidationObserver {
    override fun onAttempt(attempt: Int, max: Int) {}
    override fun onResult(result: ValidationResult) {}
    override fun onRetry(delayMs: Long) {}
}

interface RevocationStore {
    fun markRevoked(token: String)
    fun isRevoked(token: String): Boolean
}

class InMemoryRevocationStore @Inject constructor() : RevocationStore {
    private val revoked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
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
        md.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        token
    } // fall back to raw (rare)

    override fun markRevoked(token: String) {
        val h = hash(token)
        prefs.edit().putBoolean(h, true).apply()
    }

    override fun isRevoked(token: String): Boolean = prefs.getBoolean(hash(token), false)
}

object NoopRevocationStore : RevocationStore {
    override fun markRevoked(token: String) {}
    override fun isRevoked(token: String) = false
}

class RemoteSessionValidator @Inject constructor(
    @AuthClient private val client: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
    private val retryPolicy: ValidationRetryPolicy,
    private val observer: ValidationObserver = NoopValidationObserver,
    private val revocationStore: RevocationStore = NoopRevocationStore,
) : SessionValidator {

    private fun endpoint(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun validate(idToken: String): Boolean = validateDetailed(idToken).isValid

    override suspend fun validateDetailed(idToken: String): ValidationResult = withContext(Dispatchers.IO) {
        val maxAttempts = retryPolicy.maxAttempts.coerceAtLeast(1)
        var attempt = 0 // zero-based attempt index
        var last: ValidationResult
        var lastServerRetryable = false
        // All validation/sign-in goes through a single endpoint now: POST /v1/auth/google
        while (true) {
            observer.onAttempt(attempt + 1, maxAttempts)
            val path = "/v1/auth/google"
            val body = '{' + "\"idToken\":\"" + idToken + "\"}"
            if (BuildConfig.DEBUG) {
                runCatching {
                    Log.d(
                        "AuthValidate",
                        "request url=" + endpoint(path) +
                            " bodyLen=" + body.length,
                    )
                }
            }
            val req = Request.Builder()
                .url(endpoint(path))
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val result = runCatching { client.newCall(req).execute() }.fold(onSuccess = { resp ->
                resp.use { r ->
                    if (BuildConfig.DEBUG) {
                        runCatching {
                            Log.d(
                                "AuthValidate",
                                "response code=" + r.code +
                                    " retryAfter=" + (r.header("Retry-After") ?: "<none>")
                            )
                        }
                    }
                    when {
                        // Success on 200 (existing) and 201 (created)
                        r.code == 200 || r.code == 201 -> ValidationResult(true, null)
                        // Treat 400 (invalid token/body) and 401 (aud mismatch) as Unauthorized for UX
                        r.code == 400 || r.code == 401 -> ValidationResult(false, ValidationError.Unauthorized)
                        r.code in 500..599 -> {
                            // mark if server suggested retry (presence of Retry-After header)
                            lastServerRetryable = r.header("Retry-After") != null
                            ValidationResult(false, ValidationError.Server(r.code))
                        }
                        else -> ValidationResult(
                            false,
                            ValidationError.Unexpected("HTTP ${r.code}"),
                        )
                    }
                }
            }, onFailure = { e ->
                // Swallow network logging in unit tests to avoid android.util.Log dependency
                ValidationResult(false, ValidationError.Network)
            })
            last = result
            observer.onResult(result)
            val retryable = when (result.error) {
                ValidationError.Network -> true
                is ValidationError.Server -> lastServerRetryable
                else -> false
            }
            if (result.isValid || !retryable || attempt == maxAttempts - 1) break
            attempt++
            val computedDelay =
                if (attempt == 0) {
                    0L
                } else {
                    // exponential style: base * multiplier^(attempt-1)
                    val factor = Math.pow(
                        retryPolicy.backoffMultiplier,
                        (attempt - 1).toDouble(),
                    )
                    (retryPolicy.baseDelayMs * factor).toLong()
                }
            if (computedDelay > 0) {
                observer.onRetry(computedDelay)
                delay(computedDelay)
            }
        }
        last
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) {
        // No revoke endpoint for Google ID tokens in the simplified server; tokens expire naturally.
        // Keep method for interface compatibility, but perform no network I/O.
        Unit
    }
}
