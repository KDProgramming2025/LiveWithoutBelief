package info.lwb.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeRecaptchaDelegate: RecaptchaTokenProvider {
    var calls = 0
    override suspend fun getToken(action: com.google.android.recaptcha.RecaptchaAction): String? { calls++; return "tok-${calls-1}" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CachingRecaptchaProviderTest {
    @Test
    fun reusesWithinTtlAndRefreshesAfterExpiry() = runTest {
        var now = 0L
        val delegate = FakeRecaptchaDelegate()
        val cache = CachingRecaptchaProvider(delegate, ttlMillis = 1000) { now }
    val first = cache.getToken()
        assertEquals("tok-0", first)
        now = 500
    val second = cache.getToken()
        assertEquals("tok-0", second)
        now = 1500
    val third = cache.getToken()
        assertEquals("tok-1", third)
        assertEquals(2, delegate.calls) // only fetched twice
    }
}
