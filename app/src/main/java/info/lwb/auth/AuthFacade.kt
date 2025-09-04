package info.lwb.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
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
}

class FirebaseCredentialAuthFacade(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val context: Context,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        // TODO: refine credential request for Google only, configure serverClientId when available
        val request = GetCredentialRequest(listOf()) // placeholder; will populate with GoogleIdTokenCredential
        val response: GetCredentialResponse = credentialManager.getCredential(
            request = request,
            context = context
        )
        // TODO parse GoogleIdTokenCredential from response
        val idToken = null // placeholder
        require(!idToken.isNullOrBlank()) { "Missing idToken" }
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase user null after sign in")
        AuthUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
    }

    override fun currentUser(): AuthUser? = firebaseAuth.currentUser?.let { u ->
        AuthUser(u.uid, u.displayName, u.email, u.photoUrl?.toString())
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        // Credential clearing if needed (e.g., revoke tokens) will be added when backend integration exists
    }
}