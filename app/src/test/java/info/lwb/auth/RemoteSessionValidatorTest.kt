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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        validator =
            RemoteSessionValidator(
                client,
                base,
                ValidationRetryPolicy(),
            )
    }

    @Test
    fun prefsRevocationStore_persistsBetweenInstances() {
        runTest {
            // Use Robolectric application context
            val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            val store1 = PrefsRevocationStore(ctx)
            store1.markRevoked("persistToken")
            // New instance should still see revocation
            val store2 = PrefsRevocationStore(ctx)
            assertTrue(store2.isRevoked("persistToken"))
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validate_successTrue() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            val ok = validator.validate("token")
            assertTrue(ok)
        }
    }

    @Test
    fun validate_failFalse() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(401).setBody("{}")
            )
            val ok = validator.validate("token")
            assertFalse(ok)
        }
    }

    @Test
    fun validateDetailed_success() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            val res = validator.validateDetailed("token")
            assertTrue(res.isValid)
            assertNull(res.error)
        }
    }

    @Test
    fun validateDetailed_unauthorized() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(401).setBody("{}")
            )
            val res = validator.validateDetailed("token")
            assertFalse(res.isValid)
            assertEquals(ValidationError.Unauthorized, res.error)
        }
    }

    @Test
    fun validateDetailed_serverError() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(503).setBody("{}")
            )
            val res = validator.validateDetailed("token")
            assertFalse(res.isValid)
            assertTrue(res.error is ValidationError.Server)
        }
    }

    @Test
    fun revoke_doesNotThrow() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            validator.revoke("token")
            // no assertion; just ensure no crash
            assertTrue(true)
        }
    }

    @Test
    fun validateDetailed_retriesOnServerErrorAndSucceeds() {
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(503).setBody("{}").addHeader("Retry-After", "0")
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            val res = validator.validateDetailed("token")
            assertTrue(res.isValid)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun revoke_localStoreShortCircuitsValidation() {
        runTest {
            // create validator with explicit revocation store
            val revocationStore = InMemoryRevocationStore()
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("").toString().trimEnd('/'),
                    ValidationRetryPolicy(),
                    NoopValidationObserver,
                    revocationStore,
                )
            revocationStore.markRevoked("badToken")
            val res = validator.validateDetailed("badToken")
            assertFalse(res.isValid)
            assertEquals(ValidationError.Unauthorized, res.error)
            assertEquals("Expected no network calls when token is locally revoked", 0, server.requestCount)
        }
    }

    @Test
    fun validateDetailed_observerReceivesRetryEvents() {
        runTest {
            class CapturingObserver : ValidationObserver {
                val attempts = mutableListOf<Pair<Int, Int>>()
                val results = mutableListOf<ValidationResult>()
                val retryDelays = mutableListOf<Long>()
                override fun onAttempt(attempt: Int, max: Int) {
                    attempts += attempt to max
                }
                override fun onResult(result: ValidationResult) {
                    results += result
                }
                override fun onRetry(delayMs: Long) {
                    retryDelays += delayMs
                }
            }
            val obs = CapturingObserver()
            server.enqueue(
                MockResponse().setResponseCode(503).setBody("{}").addHeader("Retry-After", "0")
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("").toString().trimEnd('/'),
                    ValidationRetryPolicy(),
                    obs,
                    InMemoryRevocationStore(),
                )
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

    @Test
    fun compositeObserver_fansOutEvents() {
        runTest {
            class CObs : ValidationObserver {
                var attempts = 0
                var results = 0
                var retries = 0
                override fun onAttempt(attempt: Int, max: Int) {
                    attempts++
                }
                override fun onResult(result: ValidationResult) {
                    results++
                }
                override fun onRetry(delayMs: Long) {
                    retries++
                }
            }
            val a = CObs()
            val b = CObs()
            val composite = a.and(b)
            // Trigger a single request (200 success, no retry)
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("")
                        .toString()
                        .trimEnd('/'),
                    ValidationRetryPolicy(),
                    composite,
                    InMemoryRevocationStore(),
                )
            val res = validator.validateDetailed("token")
            assertTrue(res.isValid)
            assertEquals(1, a.attempts)
            assertEquals(1, b.attempts)
            assertEquals(1, a.results)
            assertEquals(1, b.results)
            assertEquals(0, a.retries + b.retries)
        }
    }

    @Test
    fun metricsObserver_capturesCounts() {
        runTest {
            val metrics = MetricsValidationObserver()
            val composite = metrics.and(NoopValidationObserver)
            // enqueue failure then success to produce retry + success
            server.enqueue(
                MockResponse().setResponseCode(503).setBody("{}").addHeader("Retry-After", "0")
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("").toString().trimEnd('/'),
                    ValidationRetryPolicy(),
                    composite,
                    InMemoryRevocationStore(),
                )
            val res = validator.validateDetailed("token")
            assertTrue(res.isValid)
            val snap = metrics.snapshot()
            assertTrue((snap["attempts"] ?: 0) >= 1)
            assertEquals(1, snap["success"])
            assertTrue((snap["fail"] ?: 0) >= 0)
            assertTrue((snap["retries"] ?: 0) >= 0)
        }
    }

    @Test
    fun compositeObserver_swallowsDelegateExceptions() {
        runTest {
            class BadObserver : ValidationObserver {
                override fun onAttempt(attempt: Int, max: Int) {
                    throw RuntimeException("boom")
                }
                override fun onResult(result: ValidationResult) {
                    throw RuntimeException("boom2")
                }
                override fun onRetry(delayMs: Long) {
                    throw RuntimeException("boom3")
                }
            }
            val good = object : ValidationObserver {
                var attempts = 0
                override fun onAttempt(attempt: Int, max: Int) {
                    attempts++
                }
                override fun onResult(result: ValidationResult) { }
                override fun onRetry(delayMs: Long) { }
            }
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            val composite = good.and(BadObserver())
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("").toString().trimEnd('/'),
                    ValidationRetryPolicy(),
                    composite,
                    InMemoryRevocationStore(),
                )
            val res = validator.validateDetailed("token")
            assertTrue(res.isValid)
            assertEquals(1, good.attempts) // ensured good observer still ran
        }
    }

    @Test
    fun samplingObserver_doesNotDropAllWhen1000Permille() {
        runTest {
            val metrics = MetricsValidationObserver()
            val sampled =
                SamplingValidationObserver(
                    metrics,
                    1000,
                    java.util.Random(123),
                )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{}")
            )
            validator =
                RemoteSessionValidator(
                    client,
                    server.url("")
                        .toString()
                        .trimEnd('/'),
                    ValidationRetryPolicy(),
                    sampled,
                    InMemoryRevocationStore(),
                )
            validator.validateDetailed("token")
            val snap = metrics.snapshot()
            assertTrue((snap["attempts"] ?: 0) >= 1)
        }
    }
}
