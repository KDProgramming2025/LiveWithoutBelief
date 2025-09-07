package info.lwb.auth

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.AuthResult
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseCredentialAuthFacadeTest {
    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val validator: SessionValidator = mockk(relaxed = true)
    private val signInClient: GoogleSignInClientFacade = mockk(relaxed = true)
    private val executor: GoogleSignInIntentExecutor = mockk(relaxed = true)
    private val tokenRefresher: TokenRefresher = mockk(relaxed = true)
    private val signInExecutor: SignInExecutor = mockk(relaxed = true)

    private lateinit var facade: FirebaseCredentialAuthFacade

    @Before
    fun setUp() {
    facade = FirebaseCredentialAuthFacade(firebaseAuth, context, storage, validator, signInClient, executor, tokenRefresher, signInExecutor)
    }

    @Test
    fun oneTapSignIn_silentSuccess() = runTest {
        val acct: GoogleSignInAccount = mockk {
            every { idToken } returns "token123"
        }
        every { signInClient.getLastSignedInAccount(any()) } returns acct
        mockFirebaseSignIn("uid1", "User", "user@example.com")

        val result = facade.oneTapSignIn(mockk<Activity>(relaxed = true))
        assertTrue(result.isSuccess)
    coVerify { signInExecutor.signIn("token123") }
    }

    @Ignore("Covered via instrumentation test; Activity Result requires Android environment")
    @Test
    fun oneTapSignIn_interactiveFallback() = runTest {
        every { signInClient.getLastSignedInAccount(any()) } returns null
        val acct: GoogleSignInAccount = mockk { every { idToken } returns "tokenXYZ" }
    coEvery { executor.launch(any(), any()) } returns acct
        every { signInClient.buildSignInIntent(any()) } returns mockk(relaxed = true)
        mockFirebaseSignIn("uid2", "User2", "u2@example.com")

    val activity: Activity = TestComponentActivity() // provides ComponentActivity for cast
    val result = facade.oneTapSignIn(activity)
        assertTrue(result.isSuccess)
    coVerify { signInExecutor.signIn("tokenXYZ") }
    }

    private fun mockFirebaseSignIn(uid: String, name: String?, email: String?) {
        val firebaseUser: FirebaseUser = mockk(relaxed = true) {
            every { this@mockk.uid } returns uid
            every { displayName } returns name
            every { this@mockk.email } returns email
            every { photoUrl } returns null
        }
    coEvery { signInExecutor.signIn(any()) } returns firebaseUser
    coEvery { tokenRefresher.refresh(firebaseUser, false) } returns "firebaseJwt"
    }
}
