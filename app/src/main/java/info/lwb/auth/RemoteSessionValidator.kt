package info.lwb.auth

import info.lwb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteSessionValidator @Inject constructor(): SessionValidator {
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun validate(idToken: String): Boolean = withContext(Dispatchers.IO) {
        val url = BuildConfig.AUTH_BASE_URL.trimEnd('/') + "/v1/auth/validate"
        val body = JSONObject(mapOf("idToken" to idToken)).toString()
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute() }.getOrNull()?.use { resp ->
            resp.isSuccessful
        } ?: false
    }

    override suspend fun revoke(idToken: String) = withContext(Dispatchers.IO) {
        val url = BuildConfig.AUTH_BASE_URL.trimEnd('/') + "/v1/auth/revoke"
        val body = JSONObject(mapOf("idToken" to idToken)).toString()
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { client.newCall(req).execute() }
        Unit
    }
}