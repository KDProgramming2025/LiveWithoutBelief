package info.lwb.auth

import info.lwb.auth.AuthBaseUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSessionValidatorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var validator: RemoteSessionValidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        val base = server.url("").toString().trimEnd('/')
        validator = RemoteSessionValidator(client, base)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validate_successTrue() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val ok = validator.validate("token")
        assertTrue(ok)
    }

    @Test
    fun validate_failFalse() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val ok = validator.validate("token")
        assertFalse(ok)
    }

    @Test
    fun validateDetailed_success() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val res = validator.validateDetailed("token")
        assertTrue(res.isValid)
        assertNull(res.error)
    }

    @Test
    fun validateDetailed_unauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val res = validator.validateDetailed("token")
        assertFalse(res.isValid)
        assertEquals(ValidationError.Unauthorized, res.error)
    }

    @Test
    fun validateDetailed_serverError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val res = validator.validateDetailed("token")
        assertFalse(res.isValid)
        assertTrue(res.error is ValidationError.Server)
    }


    @Test
    fun revoke_doesNotThrow() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        validator.revoke("token")
        // no assertion; just ensure no crash
        assertTrue(true)
    }
}
