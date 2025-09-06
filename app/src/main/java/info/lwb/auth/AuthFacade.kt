package info.lwb.auth

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import info.lwb.BuildConfig

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

class FirebaseCredentialAuthFacade @javax.inject.Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val sessionValidator: SessionValidator,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        // TODO(LWB-25): Migrate to new Credential Manager 1.3.0 Google ID flow (GetGoogleIdOption / etc).
        // Temporary stub: fail fast so UI can show error until proper implementation restored.
        throw UnsupportedOperationException("One-tap sign-in pending API migration")
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