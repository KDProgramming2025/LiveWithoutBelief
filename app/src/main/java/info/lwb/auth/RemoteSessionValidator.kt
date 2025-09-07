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
class RemoteSessionValidator @Inject constructor(
    @AuthClient private val client: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : SessionValidator {

    private fun endpoint(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun validate(idToken: String): Boolean = validateDetailed(idToken).isValid

    override suspend fun validateDetailed(idToken: String): ValidationResult = withContext(Dispatchers.IO) {
        val maxAttempts = 3
        var attempt = 0
        var last: ValidationResult
        var lastServerRetryable = false
        while (true) {
            val body = '{' + "\"idToken\":\"" + idToken + "\"}"
            val req = Request.Builder()
                .url(endpoint("/v1/auth/validate"))
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
            val retryable = when (result.error) {
                ValidationError.Network -> true
                is ValidationError.Server -> lastServerRetryable
                else -> false
            }
            if (result.isValid || !retryable || attempt == maxAttempts - 1) break
            attempt++
            // naive backoff (50ms,100ms)
            delay(50L * attempt)
        }
        last
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) {
    val body = '{' + "\"idToken\":\"" + idToken + "\"}" // manual JSON
        val req = Request.Builder()
            .url(endpoint("/v1/auth/revoke"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    runCatching { client.newCall(req).execute() }.getOrNull()?.close()
        Unit
    }
}