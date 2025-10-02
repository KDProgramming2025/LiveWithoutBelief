/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: RegistrationApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        val base = server.url("").toString().trimEnd('/')
        api = RemoteRegistrationApi(client, base)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun register_ignoresUnknownFields_on200() = runTest {
        val body =
            """
            {"user":{
                "id":"u-123",
                "username":"alice",
                "createdAt":"2024-09-01T12:00:00Z",
                "lastLogin":"2024-09-02T12:00:00Z",
                "extra":"ignored"
            }}
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val (user, created) = api.register("alice@example.com")

        // Assert request
        val req = server.takeRequest()
        assertEquals("/v1/auth/register", req.path)
        assertEquals("POST", req.method)
        assertTrue(req.body.readUtf8().contains("\"email\":\"alice@example.com\""))

        // Assert response mapping
        assertEquals("u-123", user.uid)
        assertEquals("alice", user.displayName)
        assertEquals("alice@example.com", user.email)
        assertFalse(created)
    }

    @Test
    fun register_setsCreatedTrue_on201() = runTest {
        val body =
            """
            {"user":{
                "id":"u-456",
                "username":"bob"
            }}
            """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(201).setBody(body))

        val (user, created) = api.register("bob@example.com")

        assertEquals("u-456", user.uid)
        assertTrue(created)
    }
}
