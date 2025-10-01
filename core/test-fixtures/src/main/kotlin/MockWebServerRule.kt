/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.testfixtures

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource

/** JUnit4 rule managing the lifecycle of a [MockWebServer] for HTTP API tests. */
class MockWebServerRule : ExternalResource() {
    lateinit var server: MockWebServer
        private set

    override fun before() {
        server = MockWebServer().also { it.start() }
    }

    override fun after() {
        server.shutdown()
    }

    fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    fun url(path: String = "/"): String = server.url(path).toString()
}
