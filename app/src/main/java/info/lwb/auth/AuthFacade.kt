package info.lwb.auth

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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

class FirebaseCredentialAuthFacade @javax.inject.Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val sessionValidator: SessionValidator,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        val comp = activity as? ComponentActivity
            ?: error("Activity must be a ComponentActivity for result API")
        // 1. Try silent sign-in first
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        val existing = GoogleSignIn.getLastSignedInAccount(activity)
        val account: GoogleSignInAccount = if (existing != null) {
            existing
        } else {
            // Interactive sign-in via Activity Result
            suspendCancellableCoroutine { cont ->
                val launcher = comp.activityResultRegistry.register("google-sign-in-${System.nanoTime()}", ActivityResultContracts.StartActivityForResult()) { result ->
                    try {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        val acc = task.getResult(ApiException::class.java)
                        cont.resume(acc)
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(RuntimeException("Sign-in failed", e)))
                    }
                }
                launcher.launch(client.signInIntent)
            }
        }
        val idToken = account.idToken ?: error("Missing idToken")
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase user null after sign in")
        val authUser = AuthUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
        secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
        user.getIdToken(false).await().token?.let { token ->
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
    val token = user.getIdToken(forceRefresh).await().token ?: error("Token refresh returned null")
    secureStorage.putIdToken(token)
    token
    }
}