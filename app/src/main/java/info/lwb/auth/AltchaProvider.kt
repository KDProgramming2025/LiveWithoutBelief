/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val BASE64_FLAGS = Base64.NO_WRAP

/**
 * Provides a mechanism to obtain an ALTCHA (proof-of-work / challenge) token from the
 * authentication backend. The token is later attached to sensitive requests to mitigate
 * automated abuse.
 */
interface AltchaTokenProvider {
    /**
     * Launches the challenge solving process and returns a Base64 encoded JSON payload
     * that includes the original challenge parameters plus the computed solution number.
     * Returns null on network / parsing / validation failure.
     */
    suspend fun solve(activity: Activity): String?
}

/**
 * Default implementation that fetches and solves an ALTCHA challenge via the HTTP API.
 * The solution is serialized to JSON and Base64 encoded for transport.
 */
@Singleton
class WebViewAltchaProvider @Inject constructor(
    @AuthBaseUrl private val baseUrl: String,
    @AuthClient private val http: OkHttpClient
) : AltchaTokenProvider {
    // No blank first line per style
    @Serializable
    private data class Challenge(
        val algorithm: String,
        val challenge: String,
        val maxnumber: Long,
        val salt: String,
        val signature: String
    )

    override suspend fun solve(activity: Activity): String? = withContext(Dispatchers.IO) {
        val challenge = fetchChallenge()
        if (challenge == null) {
            null
        } else {
            try {
                val number = solveAltcha(
                    challenge.algorithm,
                    challenge.challenge,
                    challenge.salt,
                    challenge.maxnumber
                )
                val payloadJson = buildSolutionPayload(challenge, number)
                val bytes = payloadJson.toByteArray(Charsets.UTF_8)
                val encoded = Base64.encodeToString(bytes, BASE64_FLAGS)
                encoded
            } catch (e: java.io.IOException) {
                logDebug("solve failed (io)", e)
                null
            } catch (e: IllegalStateException) {
                logDebug("solve failed (state)", e)
                null
            } catch (e: IllegalArgumentException) {
                logDebug("solve failed (arg)", e)
                null
            }
        }
    }

    private fun buildSolutionPayload(ch: Challenge, number: Long): String {
        val json = buildJsonObject {
            put("algorithm", JsonPrimitive(ch.algorithm))
            put("challenge", JsonPrimitive(ch.challenge))
            put("salt", JsonPrimitive(ch.salt))
            put("number", JsonPrimitive(number))
            put("signature", JsonPrimitive(ch.signature))
        }
        val encoded = Json.encodeToString(
            JsonObject.serializer(),
            json
        )
        return encoded
    }

    private fun fetchChallenge(): Challenge? {
        val url = baseUrl.trimEnd('/') + "/v1/altcha/challenge"
        val req = Request
            .Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val bodyStr = response.body?.string() ?: return null
            val parsed = Json.decodeFromString(
                Challenge.serializer(),
                bodyStr
            )
            return parsed
        }
    }

    private fun logDebug(msg: String, t: Throwable) {
        if (info.lwb.BuildConfig.DEBUG) {
            runCatching { android.util.Log.w("Altcha", msg, t) }
        }
    }
}
