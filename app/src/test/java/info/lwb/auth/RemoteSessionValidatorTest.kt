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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
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
    validator = RemoteSessionValidator(client, base, ValidationRetryPolicy())
    }

    @Test
    fun prefsRevocationStore_persistsBetweenInstances() = runTest {
        // Use Robolectric application context
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store1 = PrefsRevocationStore(ctx)
        store1.markRevoked("persistToken")
        // New instance should still see revocation
        val store2 = PrefsRevocationStore(ctx)
        assertTrue(store2.isRevoked("persistToken"))
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

    @Test
    fun validateDetailed_retriesOnServerErrorAndSucceeds() = runTest {
    server.enqueue(MockResponse().setResponseCode(503).setBody("{}").addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val res = validator.validateDetailed("token")
        assertTrue(res.isValid)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun revoke_localStoreShortCircuitsValidation() = runTest {
        // create validator with explicit revocation store
        val revocationStore = InMemoryRevocationStore()
        validator = RemoteSessionValidator(client, server.url("").toString().trimEnd('/'), ValidationRetryPolicy(), NoopValidationObserver, revocationStore)
        revocationStore.markRevoked("badToken")
        val res = validator.validateDetailed("badToken")
        assertFalse(res.isValid)
        assertEquals(ValidationError.Unauthorized, res.error)
        assertEquals(0, server.requestCount) // short-circuited, no network call
    }

    @Test
    fun validateDetailed_observerReceivesRetryEvents() = runTest {
        class CapturingObserver : ValidationObserver {
            val attempts = mutableListOf<Pair<Int, Int>>()
            val results = mutableListOf<ValidationResult>()
            val retryDelays = mutableListOf<Long>()
            override fun onAttempt(attempt: Int, max: Int) { attempts += attempt to max }
            override fun onResult(result: ValidationResult) { results += result }
            override fun onRetry(delayMs: Long) { retryDelays += delayMs }
        }
        val obs = CapturingObserver()
        // First a retryable 503 with header then success
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}").addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        validator = RemoteSessionValidator(client, server.url("").toString().trimEnd('/'), ValidationRetryPolicy(), obs, InMemoryRevocationStore())
        val res = validator.validateDetailed("token")
        assertTrue(res.isValid)
        assertEquals(2, server.requestCount)
        // Observer assertions
        assertTrue("Expected at least one attempt", obs.attempts.isNotEmpty())
        assertEquals(1, obs.attempts.first().first)
        // If a retry happened, we should have a retry delay
        if (obs.attempts.size > 1) {
            assertTrue("Retry delay missing though multiple attempts recorded", obs.retryDelays.isNotEmpty())
        }
        assertTrue("Expected at least one result callback", obs.results.isNotEmpty())
    }
}
