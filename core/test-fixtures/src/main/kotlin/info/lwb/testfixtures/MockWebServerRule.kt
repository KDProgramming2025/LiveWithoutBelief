/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.testfixtures

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource

/** JUnit4 rule managing the lifecycle of a [MockWebServer] for HTTP API tests. */
class MockWebServerRule : ExternalResource() {
    /** Active underlying [MockWebServer] instance started in [before]. */
    lateinit var server: MockWebServer
        private set

    override fun before() {
        server = MockWebServer().also { it.start() }
    }

    override fun after() {
        server.shutdown()
    }

    /** Enqueues a JSON response with optional HTTP [code] (default 200). */
    fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    /** Returns a full URL for the provided [path] using this rule's [server] base URL. */
    fun url(path: String = "/"): String = server.url(path).toString()
}
