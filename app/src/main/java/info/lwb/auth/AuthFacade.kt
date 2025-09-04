package info.lwb.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.PasswordCredential
import androidx.credentials.Credential
import androidx.credentials.CredentialOption
import androidx.credentials.ProviderCreateCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.GetSignInCredentialOption
import androidx.credentials.SignInCredential
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
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        // NOTE: serverClientId should be injected/configured (build config or remote config) – placeholder now
        val serverClientId = "REPLACE_ME_SERVER_CLIENT_ID"
        val option = GetSignInCredentialOption.Builder()
            .setServerClientId(serverClientId)
            .setNonce(null)
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
    user.getIdToken(false).await().token?.let { secureStorage.putIdToken(it) }
    authUser
    }

    override fun currentUser(): AuthUser? = firebaseAuth.currentUser?.let { u ->
        AuthUser(u.uid, u.displayName, u.email, u.photoUrl?.toString())
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    secureStorage.clear()
    // TODO revoke server session when backend available (LWB-30 extension)
    }

    override suspend fun refreshIdToken(forceRefresh: Boolean): Result<String> = runCatching {
        val user = firebaseAuth.currentUser ?: error("No user to refresh")
    val token = user.getIdToken(forceRefresh).await().token ?: error("Token refresh returned null")
    secureStorage.putIdToken(token)
    token
    }
}