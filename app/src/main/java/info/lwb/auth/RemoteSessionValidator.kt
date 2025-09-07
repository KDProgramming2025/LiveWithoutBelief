package info.lwb.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteSessionValidator @Inject constructor(
    private val client: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : SessionValidator {

    private fun endpoint(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun validate(idToken: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject(mapOf("idToken" to idToken)).toString()
        val req = Request.Builder()
            .url(endpoint("/v1/auth/validate"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute() }
            .onFailure { Log.w("RemoteSessionValidator", "validate network error: ${it.message}") }
            .getOrNull()?.use { resp ->
                resp.isSuccessful
            } ?: false
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) {
        val body = JSONObject(mapOf("idToken" to idToken)).toString()
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