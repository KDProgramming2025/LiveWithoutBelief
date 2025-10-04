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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for performing password-based authentication requests.
 * Implementations communicate with the backend to register and login users.
 */
interface PasswordAuthApi {
    /** Register a new user with an altcha challenge solution. */
    suspend fun register(username: String, password: String, altcha: String): AuthUser

    /** Login with username & password. */
    suspend fun login(username: String, password: String): AuthUser
}

/**
 * Remote HTTP implementation of [PasswordAuthApi] using OkHttp.
 */
@Singleton
class RemotePasswordAuthApi @Inject constructor(
    @AuthClient private val http: OkHttpClient,
    @AuthBaseUrl private val baseUrl: String,
) : PasswordAuthApi {
    // Internal request/response DTOs

    @Serializable
    private data class PwdReq(val username: String, val password: String)

    @Serializable
    private data class PwdRegReq(val username: String, val password: String, val altcha: String)

    @Serializable
    private data class ServerUser(val id: String, val username: String? = null)

    @Serializable
    private data class PwdRes(val user: ServerUser)

    private val json = Json { ignoreUnknownKeys = true }

    // Constants extracted from previous magic numbers / strings.
    private companion object {
        private const val CONTENT_JSON = "application/json"
        private const val HEADER_AUTH_REASON = "X-Auth-Reason"
        private const val HEADER_ALTCHA_EXPIRED = "X-Altcha-Expired"
        private const val STATUS_CONFLICT = 409
        private const val STATUS_OK = 200
        private const val STATUS_UNAUTHORIZED = 401
        private const val PATH_REGISTER = "/v1/auth/pwd/register"
        private const val PATH_LOGIN = "/v1/auth/pwd/login"
        private const val ERR_USER_EXISTS = "user_exists"
        private const val ERR_REGISTER_FAILED = "register_failed"
        private const val ERR_INVALID_CREDENTIALS = "invalid_credentials"
        private const val ERR_LOGIN_FAILED = "login_failed"
    }

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    override suspend fun register(username: String, password: String, altcha: String): AuthUser =
        withContext(Dispatchers.IO) {
            val request = buildRegisterRequest(username, password, altcha)
            http.newCall(request).execute().use { response ->
                handleRegisterErrors(response)
                parseUser(response)
            }
        }

    private fun buildRegisterRequest(username: String, password: String, altcha: String): Request {
        val payload = json.encodeToString(
            PwdRegReq.serializer(),
            PwdRegReq(username, password, altcha),
        )
        return Request
            .Builder()
            .url(url(PATH_REGISTER))
            .post(payload.toRequestBody(CONTENT_JSON.toMediaType()))
            .build()
    }

    private fun handleRegisterErrors(r: okhttp3.Response) {
        if (r.code == STATUS_CONFLICT) {
            error(ERR_USER_EXISTS)
        }
        if (r.code != STATUS_OK) {
            val reason = r.header(HEADER_AUTH_REASON)
            val expired = r.header(HEADER_ALTCHA_EXPIRED)
            val msg = buildString {
                append(ERR_REGISTER_FAILED).append(' ').append(r.code)
                if (!reason.isNullOrBlank()) {
                    append(" reason=").append(reason)
                }
                if (!expired.isNullOrBlank()) {
                    append(" expired=").append(expired)
                }
            }
            error(msg)
        }
    }

    private fun parseUser(r: okhttp3.Response): AuthUser {
        val res = json.decodeFromString(PwdRes.serializer(), r.body!!.string())
        return AuthUser(
            uid = res.user.id,
            displayName = res.user.username,
            email = null,
            photoUrl = null,
        )
    }

    override suspend fun login(username: String, password: String): AuthUser = withContext(Dispatchers.IO) {
        val request = buildLoginRequest(username, password)
        http.newCall(request).execute().use { response ->
            if (response.code == STATUS_UNAUTHORIZED) {
                error(ERR_INVALID_CREDENTIALS)
            }
            if (response.code != STATUS_OK) {
                error("$ERR_LOGIN_FAILED ${response.code}")
            }
            parseUser(response)
        }
    }

    private fun buildLoginRequest(username: String, password: String): Request {
        val payload = json.encodeToString(
            PwdReq.serializer(),
            PwdReq(username, password),
        )
        return Request
            .Builder()
            .url(url(PATH_LOGIN))
            .post(payload.toRequestBody(CONTENT_JSON.toMediaType()))
            .build()
    }
}
