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

/**
 * Abstraction for registering a user account given an email address.
 * Implementations should be idempotent: creating a user if one does not exist
 * and returning the existing user otherwise.
 *
 * @return Pair(AuthUser, created) where created indicates whether a new user was created.
 */
interface RegistrationApi {
    /**
     * Register (or fetch existing) user associated with [email].
     * @return Pair(user, created) where created is true if a new user record was created.
     */
    suspend fun register(email: String): Pair<AuthUser, Boolean>
}

/**
 * Network-backed implementation of [RegistrationApi] using OkHttp + kotlinx.serialization.
 * Performs a POST to /v1/auth/register and interprets 200 vs 201 to signal existing vs newly created user.
 */
@Singleton
class RemoteRegistrationApi @Inject constructor(
    @AuthClient private val http: okhttp3.OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : RegistrationApi {
    @Serializable private data class RegisterReq(val email: String)

    @Serializable private data class ServerUser(
        val id: String,
        val username: String? = null,
        // Server may include createdAt/lastLogin fields; we ignore them via parser config
        val createdAt: String? = null,
        val lastLogin: String? = null,
    )

    @Serializable private data class RegisterRes(val user: ServerUser)

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val MAX_ERROR_BODY_PREVIEW = 200
        private const val REGISTER_PATH = "/v1/auth/register"
    }

    override suspend fun register(email: String): Pair<AuthUser, Boolean> = withContext(Dispatchers.IO) {
        val trimmedBase = baseUrl.trimEnd('/')
        val url = trimmedBase + REGISTER_PATH
        val payload = json
            .encodeToString(RegisterReq.serializer(), RegisterReq(email))
        val jsonMedia = "application/json".toMediaType()
        val bodyReq = payload.toRequestBody(jsonMedia)
        val reqBuilder = okhttp3.Request.Builder()
        reqBuilder.url(url)
        reqBuilder.post(bodyReq)
        val req = reqBuilder.build()
        val call = http.newCall(req)
        val resp = call.execute()
        resp.use { r ->
            if (r.code != HTTP_OK && r.code != HTTP_CREATED) {
                val body = r.body?.string()
                error("register failed (${r.code}): ${body?.take(MAX_ERROR_BODY_PREVIEW) ?: "<no-body>"}")
            }
            val body = r.body?.string() ?: error("empty register body")
            val parsed = json
                .decodeFromString(RegisterRes.serializer(), body)
            val created = r.code == HTTP_CREATED
            // Map server user to app AuthUser; we keep email/displayName from Google elsewhere.
            AuthUser(
                uid = parsed.user.id,
                displayName = parsed.user.username,
                email = email,
                photoUrl = null,
            ) to created
        }
    }
}
