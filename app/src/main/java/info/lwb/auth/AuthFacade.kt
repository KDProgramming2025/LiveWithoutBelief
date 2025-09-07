package info.lwb.auth

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import androidx.activity.ComponentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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

/** Optional abstraction for One Tap / Credential Manager based Google ID token retrieval. */
interface OneTapCredentialProvider {
    suspend fun getIdToken(activity: Activity): String?
}

/**
 * Abstraction over Firebase token refresh so unit tests can mock without dealing with Task APIs.
 */
interface TokenRefresher {
    suspend fun refresh(user: com.google.firebase.auth.FirebaseUser, force: Boolean): String
}

class FirebaseTokenRefresher @javax.inject.Inject constructor() : TokenRefresher {
    override suspend fun refresh(user: com.google.firebase.auth.FirebaseUser, force: Boolean): String {
        return user.getIdToken(force).await().token ?: error("Token refresh returned null")
    }
}

interface SignInExecutor {
    suspend fun signIn(idToken: String): com.google.firebase.auth.FirebaseUser
}

class FirebaseSignInExecutor @javax.inject.Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : SignInExecutor {
    override suspend fun signIn(idToken: String): com.google.firebase.auth.FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebaseAuth.signInWithCredential(credential).await().user
            ?: error("Firebase user null after sign in")
    }
}

class FirebaseCredentialAuthFacade @javax.inject.Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val sessionValidator: SessionValidator,
    private val signInClient: GoogleSignInClientFacade,
    private val intentExecutor: GoogleSignInIntentExecutor,
    private val tokenRefresher: TokenRefresher,
    private val signInExecutor: SignInExecutor,
    private val oneTapProvider: OneTapCredentialProvider,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        val comp = activity as? ComponentActivity
        // 1. Try silent sign-in (cached GoogleSignIn account)
        val existing = signInClient.getLastSignedInAccount(activity)
        val idToken: String = when {
            existing?.idToken != null -> existing.idToken!!
            else -> {
                // 2. Try One Tap (Credential Manager)
                val oneTapToken = oneTapProvider.getIdToken(activity)
                if (oneTapToken != null) oneTapToken else {
                    // 3. Fallback to interactive classic Google Sign-In
                    requireNotNull(comp) { "Activity must be a ComponentActivity for interactive sign-in" }
                    val acct = intentExecutor.launch(comp) { signInClient.buildSignInIntent(activity) }
                    acct.idToken ?: error("Missing idToken from interactive sign-in")
                }
            }
        }
        val user = signInExecutor.signIn(idToken)
        val authUser = AuthUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
        secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
    runCatching { tokenRefresher.refresh(user, false) }.getOrNull()?.let { token ->
            secureStorage.putIdToken(token)
            sessionValidator.validate(token)
        }
        authUser
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
    val token = tokenRefresher.refresh(user, forceRefresh)
    secureStorage.putIdToken(token)
    val parsedExp = JwtUtils.extractExpiryEpochSeconds(token)
    val storeExp = when {
        parsedExp == null -> (System.currentTimeMillis() / 1000L) + (50 * 60) // fallback heuristic
        else -> parsedExp - 5 * 60 // refresh 5m early
    }
    secureStorage.putTokenExpiry(storeExp)
    token
    }
}