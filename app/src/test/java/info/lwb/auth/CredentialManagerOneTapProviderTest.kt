package info.lwb.auth

import android.app.Activity
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.test.runTest

/**
 * Basic sanity test: provider should return null (no crash) when run in a plain Activity
 * under Robolectric without real Credential Manager interaction.
 */
class CredentialManagerOneTapProviderTest {
    @Test
    fun returnsNullGracefullyWithoutServices() = runTest {
        val fakeCall = object : CredentialCall {
            override suspend fun get(
                activity: Activity,
                request: androidx.credentials.GetCredentialRequest
            ): androidx.credentials.GetCredentialResponse {
                throw UnsupportedOperationException("fake")
            }
        }
        val provider = CredentialManagerOneTapProvider(fakeCall)
        val activity = Activity() // Robolectric plain activity
        val token = provider.getIdToken(activity)
        assertNull(token)
    }
}