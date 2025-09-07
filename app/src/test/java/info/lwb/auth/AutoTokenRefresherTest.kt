package info.lwb.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.delay
import org.junit.Test

private class InMemoryStorage : SecureStorage {
    private var tokenValue: String? = null
    private var expValue: Long? = null
    private var profileTriple: Triple<String?, String?, String?> = Triple(null, null, null)
    override fun putIdToken(token: String) { tokenValue = token }
    override fun getIdToken(): String? = tokenValue
    override fun putTokenExpiry(epochSeconds: Long?) { expValue = epochSeconds }
    override fun getTokenExpiry(): Long? = expValue
    override fun clear() { tokenValue = null; expValue = null; profileTriple = Triple(null,null,null) }
    override fun putProfile(name: String?, email: String?, avatar: String?) { profileTriple = Triple(name,email,avatar) }
    override fun getProfile(): Triple<String?, String?, String?> = profileTriple
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutoTokenRefresherTest {
    @Test
    fun triggersRefreshWhenNearExpiry() = runTest(UnconfinedTestDispatcher()) {
        val storage = InMemoryStorage().apply {
            val soon = (System.currentTimeMillis()/1000L) + 100 // 100s from now
            putTokenExpiry(soon)
        }
        val authFacade: AuthFacade = mockk(relaxed = true)
        coEvery { authFacade.refreshIdToken(false) } returns Result.success("tok")
    val refresher = AutoTokenRefresher(storage, authFacade, this, TokenRefreshConfig(refreshLeadTimeSeconds = 5*60, pollIntervalSeconds = 1))
        refresher.start()
        // allow a couple poll iterations
        delay(1500)
        coVerify(atLeast = 1) { authFacade.refreshIdToken(false) }
        refresher.stop()
        assertTrue(true)
    }

    @Test
    fun doesNotRefreshWhenFarFromExpiry() = runTest(UnconfinedTestDispatcher()) {
        val storage = InMemoryStorage().apply {
            val far = (System.currentTimeMillis()/1000L) + 3600 // 1h
            putTokenExpiry(far)
        }
        val authFacade: AuthFacade = mockk(relaxed = true)
        coEvery { authFacade.refreshIdToken(false) } returns Result.success("tok")
    val refresher = AutoTokenRefresher(storage, authFacade, this, TokenRefreshConfig(refreshLeadTimeSeconds = 5*60, pollIntervalSeconds = 1))
        refresher.start()
        delay(1200)
        coVerify(exactly = 0) { authFacade.refreshIdToken(false) }
        refresher.stop()
        assertTrue(true)
    }
}
