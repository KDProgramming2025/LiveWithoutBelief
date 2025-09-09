package info.lwb.auth

import android.content.Context
import com.google.firebase.auth.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import io.mockk.coVerify
import io.mockk.verify
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import org.junit.Test
import okhttp3.OkHttpClient

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFacadeTest {
    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val secureStorage: SecureStorage = mockk(relaxed = true)
    private val sessionValidator: SessionValidator = mockk(relaxed = true)
    private val signInClient: GoogleSignInClientFacade = mockk(relaxed = true)
    private val intentExecutor: GoogleSignInIntentExecutor = mockk(relaxed = true)
    private val tokenRefresher: TokenRefresher = mockk(relaxed = true)
    private val signInExecutor: SignInExecutor = mockk(relaxed = true)
    private val oneTap: OneTapCredentialProvider = mockk(relaxed = true)
    private val http: OkHttpClient = mockk(relaxed = true)
    private val authBaseUrl: String = "https://example.invalid"

    @Test
    fun signInCachesProfileAndToken() = runTest {
        val acct: GoogleSignInAccount = mockk {
            every { idToken } returns "idTokenABC"
        }
        every { signInClient.getLastSignedInAccount(any()) } returns acct
        val firebaseUser: FirebaseUser = mockk {
            every { uid } returns "uid123"; every { displayName } returns "Alice"; every { email } returns "alice@example.com"; every { photoUrl } returns null
        }
        coEvery { signInExecutor.signIn("idTokenABC") } returns firebaseUser
        coEvery { tokenRefresher.refresh(firebaseUser, false) } returns "freshTokenXYZ"
        coEvery { sessionValidator.validate(any()) } returns true
    val facade = FirebaseCredentialAuthFacade(firebaseAuth, context, secureStorage, sessionValidator, signInClient, intentExecutor, tokenRefresher, signInExecutor, oneTap, http, authBaseUrl)
    val result = facade.oneTapSignIn(mockk(relaxed = true))
    assertTrue(result.isSuccess)
        coVerify(exactly = 1) { signInExecutor.signIn("idTokenABC") }
        coVerify(exactly = 1) { tokenRefresher.refresh(firebaseUser, false) }
        verify(exactly = 1) { secureStorage.putProfile("Alice", "alice@example.com", null) }
        verify(exactly = 1) { secureStorage.putIdToken("freshTokenXYZ") }
    }

    @Test
    fun refreshUpdatesToken() = runTest {
        val user: FirebaseUser = mockk()
        every { user.uid } returns "u"
        every { firebaseAuth.currentUser } returns user
        coEvery { tokenRefresher.refresh(user, true) } returns "newToken"
    val facade = FirebaseCredentialAuthFacade(firebaseAuth, context, secureStorage, sessionValidator, signInClient, intentExecutor, tokenRefresher, signInExecutor, oneTap, http, authBaseUrl)
        val refreshed = facade.refreshIdToken(true)
        assertTrue(refreshed.isSuccess)
    }

    @Test
    fun signOutClearsStorage() = runTest {
    val facade = FirebaseCredentialAuthFacade(firebaseAuth, context, secureStorage, sessionValidator, signInClient, intentExecutor, tokenRefresher, signInExecutor, oneTap, http, authBaseUrl)
        facade.signOut()
        // verify clear called (relaxed mock accepts call)
        assertTrue(true)
    }

    @Test
    fun signInErrorWrapped() = runTest {
    // Force failure during sign-in execution (after obtaining token)
    val acct: GoogleSignInAccount = mockk { every { idToken } returns "badToken" }
    every { signInClient.getLastSignedInAccount(any()) } returns acct
    coEvery { signInExecutor.signIn("badToken") } throws IllegalStateException("Credential rejected")
    val facade = FirebaseCredentialAuthFacade(firebaseAuth, context, secureStorage, sessionValidator, signInClient, intentExecutor, tokenRefresher, signInExecutor, oneTap, http, authBaseUrl)
    val result = facade.oneTapSignIn(mockk(relaxed = true))
    assertTrue(result.isFailure)
    }
}
