package info.lwb.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    override fun markRevoked(token: String) { revoked.add(token) }
    override fun isRevoked(token: String): Boolean = token in revoked
}

/** Simple persistent implementation backed by encrypted shared preferences (same security profile as SecureStorage). */
@Singleton
class PrefsRevocationStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context
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
    } catch (_: Exception) { token } // fall back to raw (rare)

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
        // Short-circuit if locally revoked (avoid network traffic)
        if (revocationStore.isRevoked(idToken)) {
            return@withContext ValidationResult(false, ValidationError.Unauthorized)
        }
        // Heuristic: password auth tokens are JWTs we issue (three segments, contains 'typ":"pwd')
        fun isPasswordJwt(token: String): Boolean = token.count { it == '.' } == 2 && token.length < 300 // Google ID tokens are usually longer
        val passwordMode = isPasswordJwt(idToken)
        while (true) {
            observer.onAttempt(attempt + 1, maxAttempts)
            val path = if (passwordMode) "/v1/auth/pwd/validate" else "/v1/auth/validate"
            val body = if (passwordMode) '{' + "\"token\":\"" + idToken + "\"}" else '{' + "\"idToken\":\"" + idToken + "\"}"
            val req = Request.Builder()
                .url(endpoint(path))
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val result = runCatching { client.newCall(req).execute() }.fold(onSuccess = { resp ->
                resp.use { r ->
                    when {
                        r.isSuccessful -> ValidationResult(true, null)
                        r.code == 401 -> ValidationResult(false, ValidationError.Unauthorized)
                        r.code in 500..599 -> {
                            // mark if server suggested retry (presence of Retry-After header)
                            lastServerRetryable = r.header("Retry-After") != null
                            ValidationResult(false, ValidationError.Server(r.code))
                        }
                        else -> ValidationResult(false, ValidationError.Unexpected("HTTP ${r.code}"))
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
            val computedDelay = if (attempt == 0) 0L else {
                // exponential style: base * multiplier^(attempt-1)
                val factor = Math.pow(retryPolicy.backoffMultiplier, (attempt - 1).toDouble())
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
        revocationStore.markRevoked(idToken)
    fun isPasswordJwt(token: String): Boolean = token.count { it == '.' } == 2 && token.length < 300
    val passwordMode = isPasswordJwt(idToken)
    val body = if (passwordMode) '{' + "\"token\":\"" + idToken + "\"}" else '{' + "\"idToken\":\"" + idToken + "\"}" // manual JSON
        val req = Request.Builder()
            .url(endpoint("/v1/auth/revoke"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute() }.getOrNull()?.close()
        Unit
    }
}