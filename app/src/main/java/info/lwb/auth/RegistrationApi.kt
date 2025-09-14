/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

interface RegistrationApi {
    /**
     * Registers the user by email. Creates if missing, no-op if exists.
     * Returns Pair(user, created) where created is true if newly created.
     */
    suspend fun register(email: String): Pair<AuthUser, Boolean>
}

@Singleton
class RemoteRegistrationApi @Inject constructor(
    @AuthClient private val http: okhttp3.OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : RegistrationApi {

    @Serializable private data class RegisterReq(val email: String)
    @Serializable private data class ServerUser(val id: String, val username: String? = null)
    @Serializable private data class RegisterRes(val user: ServerUser)

    override suspend fun register(email: String): Pair<AuthUser, Boolean> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v1/auth/register"
        val payload = Json.encodeToString(RegisterReq.serializer(), RegisterReq(email))
        val req = okhttp3.Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        resp.use { r ->
            if (r.code != 200 && r.code != 201) {
                val body = r.body?.string()
                error("register failed (${r.code}): ${body?.take(200) ?: "<no-body>"}")
            }
            val body = r.body?.string() ?: error("empty register body")
            val parsed = Json.decodeFromString(RegisterRes.serializer(), body)
            val created = r.code == 201
            // Map server user to app AuthUser; we keep email/displayName from Google elsewhere.
            AuthUser(uid = parsed.user.id, displayName = parsed.user.username, email = email, photoUrl = null) to created
        }
    }
}
