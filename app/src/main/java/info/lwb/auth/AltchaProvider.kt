/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

interface AltchaTokenProvider {
    suspend fun solve(activity: Activity): String?
}

@Singleton
class WebViewAltchaProvider @Inject constructor(
    @AuthBaseUrl private val baseUrl: String,
    @AuthClient private val http: OkHttpClient,
) : AltchaTokenProvider {
    @Serializable private data class Challenge(
        val algorithm: String,
        val challenge: String,
        val maxnumber: Long,
        val salt: String,
        val signature: String,
    )

    override suspend fun solve(activity: Activity): String? = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + "/v1/altcha/challenge"
            val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val json = r.body?.string() ?: return@withContext null
                val ch = Json.decodeFromString(Challenge.serializer(), json)
                // IMPORTANT: use salt exactly as provided by server
                val n = solveAltcha(ch.algorithm, ch.challenge, ch.salt, ch.maxnumber)
                val payloadJson = Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.buildJsonObject {
                        put("algorithm", kotlinx.serialization.json.JsonPrimitive(ch.algorithm))
                        put("challenge", kotlinx.serialization.json.JsonPrimitive(ch.challenge))
                        put("salt", kotlinx.serialization.json.JsonPrimitive(ch.salt))
                        put("number", kotlinx.serialization.json.JsonPrimitive(n))
                        put("signature", kotlinx.serialization.json.JsonPrimitive(ch.signature))
                    },
                )
                android.util.Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            if (info.lwb.BuildConfig.DEBUG) {
                runCatching { android.util.Log.w("Altcha", "solve failed", e) }
            }
            null
        }
    }
}
