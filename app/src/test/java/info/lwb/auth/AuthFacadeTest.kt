package info.lwb.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetSignInCredentialOption
import androidx.credentials.SignInCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFacadeTest {
    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
    private val credentialManager: CredentialManager = mockk()
    private val context: Context = mockk(relaxed = true)
    private val secureStorage: SecureStorage = mockk(relaxed = true)

    @Test
    fun signInCachesProfileAndToken() = runTest {
        val user: FirebaseUser = mockk()
        every { user.uid } returns "uid1"
        every { user.displayName } returns "Test User"
        every { user.email } returns "test@example.com"
        every { user.photoUrl } returns null
        val authResult: AuthResult = mockk()
        every { authResult.user } returns user
        coEvery { firebaseAuth.signInWithCredential(any()).await() } returns authResult
        coEvery { user.getIdToken(false).await() } returns GetTokenResult(mapOf("token" to "abc"))
        val signInCred: SignInCredential = mockk()
        every { signInCred.googleIdToken } returns "idToken123"
        val response: GetCredentialResponse = GetCredentialResponse(signInCred)
        coEvery { credentialManager.getCredential(any(), any()) } returns response

        val facade = FirebaseCredentialAuthFacade(firebaseAuth, credentialManager, context, secureStorage)
        val result = facade.oneTapSignIn(mockk(relaxed = true))
        assertTrue(result.isSuccess)
        assertEquals("uid1", result.getOrThrow().uid)
    }
}
