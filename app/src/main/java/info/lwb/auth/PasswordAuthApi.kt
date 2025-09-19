/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

interface PasswordAuthApi {
    suspend fun register(username: String, password: String, altcha: String): AuthUser
    suspend fun login(username: String, password: String): AuthUser
}

@Singleton
class RemotePasswordAuthApi @Inject constructor(
    @AuthClient private val http: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : PasswordAuthApi {
    @Serializable private data class PwdReq(val username: String, val password: String)
    @Serializable private data class PwdRegReq(val username: String, val password: String, val altcha: String)
    @Serializable private data class ServerUser(val id: String, val username: String? = null)
    @Serializable private data class PwdRes(val user: ServerUser)
    private val json = Json { ignoreUnknownKeys = true }

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun register(username: String, password: String, altcha: String): AuthUser = withContext(Dispatchers.IO) {
    val body = json.encodeToString(PwdRegReq.serializer(), PwdRegReq(username, password, altcha))
        val req = Request.Builder()
            .url(url("/v1/auth/pwd/register"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 409) error("user_exists")
            if (r.code != 200) {
                val reason = r.header("X-Auth-Reason")
                val expired = r.header("X-Altcha-Expired")
                val msg = buildString {
                    append("register_failed ").append(r.code)
                    if (!reason.isNullOrBlank()) append(" reason=").append(reason)
                    if (!expired.isNullOrBlank()) append(" expired=").append(expired)
                }
                error(msg)
            }
            val res = json.decodeFromString(PwdRes.serializer(), r.body!!.string())
            AuthUser(uid = res.user.id, displayName = res.user.username, email = null, photoUrl = null)
        }
    }

    override suspend fun login(username: String, password: String): AuthUser = withContext(Dispatchers.IO) {
    val body = json.encodeToString(PwdReq.serializer(), PwdReq(username, password))
        val req = Request.Builder()
            .url(url("/v1/auth/pwd/login"))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401) error("invalid_credentials")
            if (r.code != 200) error("login_failed ${r.code}")
            val res = json.decodeFromString(PwdRes.serializer(), r.body!!.string())
            AuthUser(uid = res.user.id, displayName = res.user.username, email = null, photoUrl = null)
        }
    }
}
