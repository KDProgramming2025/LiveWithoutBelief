package info.lwb.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetSignInCredentialOption
import androidx.credentials.SignInCredential
import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

interface AuthFacade {
    suspend fun oneTapSignIn(activity: Activity): Result<AuthUser>
    fun currentUser(): AuthUser?
    suspend fun signOut()
    suspend fun refreshIdToken(forceRefresh: Boolean = false): Result<String>
}

class FirebaseCredentialAuthFacade(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val context: Context,
    private val secureStorage: SecureStorage,
    private val sessionValidator: SessionValidator,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        // NOTE: serverClientId should be injected/configured (build config or remote config) – placeholder now
        try {
            val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
            val option = GetSignInCredentialOption.Builder()
                .setServerClientId(serverClientId)
                .build()
            val request = GetCredentialRequest(listOf(option))
            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
            )
            val signInCred = response.credential as? SignInCredential
                ?: error("Unexpected credential type: ${response.credential::class.java.simpleName}")
            val idToken = signInCred.googleIdToken
            require(!idToken.isNullOrBlank()) { "Missing idToken" }
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val user = firebaseAuth.signInWithCredential(firebaseCredential).await().user
                ?: error("Firebase user null after sign in")
            val authUser = AuthUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
            // Cache minimal profile (LWB-31) and latest id token for reuse (LWB-29)
            secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
            user.getIdToken(false).await().token?.let { token ->
                secureStorage.putIdToken(token)
                // Validate with backend (placeholder always true for now)
                sessionValidator.validate(token)
            }
            authUser
        } catch (e: GetCredentialException) {
            throw e // propagate for UI differentiation if needed
        } catch (e: Exception) {
            throw RuntimeException("Sign-in failed", e)
        }
    }

    override fun currentUser(): AuthUser? = firebaseAuth.currentUser?.let { u ->
        AuthUser(u.uid, u.displayName, u.email, u.photoUrl?.toString())
    }

    override suspend fun signOut() {
    val token = secureStorage.getIdToken()
    runCatching { if (token != null) sessionValidator.revoke(token) }
    firebaseAuth.signOut()
    secureStorage.clear()
    }

    override suspend fun refreshIdToken(forceRefresh: Boolean): Result<String> = runCatching {
        val user = firebaseAuth.currentUser ?: error("No user to refresh")
    val token = user.getIdToken(forceRefresh).await().token ?: error("Token refresh returned null")
    secureStorage.putIdToken(token)
    token
    }
}