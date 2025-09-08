package info.lwb.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import info.lwb.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Facade abstraction to allow mocking Google Sign-In flows in unit tests without depending on
 * Google Play Services classes directly in tests.
 */
interface GoogleSignInClientFacade {
    fun getLastSignedInAccount(activity: Activity): GoogleSignInAccount?
    fun buildSignInIntent(activity: Activity): Intent
}

class DefaultGoogleSignInClientFacade : GoogleSignInClientFacade {
    override fun getLastSignedInAccount(activity: Activity): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(activity)

    override fun buildSignInIntent(activity: Activity): Intent {
        // IMPORTANT: requestIdToken must use the WEB (server) client id from Firebase (client_type=3) for FirebaseAuth.signInWithCredential
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .requestEmail()
            .build()
        if (BuildConfig.DEBUG) android.util.Log.d(
            "AuthFlow",
            "GoogleSignInOptions using serverId=${BuildConfig.GOOGLE_SERVER_CLIENT_ID.take(18)}… androidId=${BuildConfig.GOOGLE_ANDROID_CLIENT_ID.take(18)}…"
        )
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }
}

/** Executor abstraction for launching the interactive sign-in and returning the account. */
interface GoogleSignInIntentExecutor {
    suspend fun launch(activity: ComponentActivity, intentProvider: () -> Intent): GoogleSignInAccount
}

class ActivityResultGoogleSignInExecutor : GoogleSignInIntentExecutor {
    override suspend fun launch(
        activity: ComponentActivity,
        intentProvider: () -> Intent,
    ): GoogleSignInAccount = suspendCancellableCoroutine { cont ->
        val key = "google-sign-in-${System.nanoTime()}"
        val launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val acc = task.getResult(ApiException::class.java)
                cont.resume(acc)
            } catch (e: Exception) {
                cont.resumeWith(Result.failure(RuntimeException("Sign-in failed", e)))
            }
        }
        launcher.launch(intentProvider())
    }
}
