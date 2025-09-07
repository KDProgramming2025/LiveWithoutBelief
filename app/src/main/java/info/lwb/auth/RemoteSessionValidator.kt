package info.lwb.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    val body = '{' + "\"idToken\":\"" + idToken + "\"}" // simple manual JSON avoids Android JSONObject in unit tests
        val req = Request.Builder()
            .url(endpoint("/v1/auth/validate"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val callResult = runCatching { client.newCall(req).execute() }
        callResult.fold(onSuccess = { resp ->
            resp.use { r ->
                when {
                    r.isSuccessful -> ValidationResult(true, null)
                    r.code == 401 -> ValidationResult(false, ValidationError.Unauthorized)
                    r.code in 500..599 -> ValidationResult(false, ValidationError.Server(r.code))
                    else -> ValidationResult(false, ValidationError.Unexpected("HTTP ${r.code}"))
                }
            }
        }, onFailure = { e ->
            Log.w("RemoteSessionValidator", "validate network error: ${e.message}")
            ValidationResult(false, ValidationError.Network)
        })
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) {
    val body = '{' + "\"idToken\":\"" + idToken + "\"}" // manual JSON
        val req = Request.Builder()
            .url(endpoint("/v1/auth/revoke"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute() }
            .onFailure { Log.w("RemoteSessionValidator", "revoke network error: ${it.message}") }
            .getOrNull()?.close()
        Unit
    }
}