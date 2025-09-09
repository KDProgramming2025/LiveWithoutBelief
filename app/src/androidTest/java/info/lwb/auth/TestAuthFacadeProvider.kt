package info.lwb.auth

import android.app.Activity
import dagger.hilt.android.testing.HiltAndroidTest
import com.google.firebase.auth.FirebaseAuth
import androidx.test.platform.app.InstrumentationRegistry

/** Lightweight provider without full Hilt graph for auth instrumentation smoke. */
object TestAuthFacadeProvider {
    fun provide(activity: Activity): AuthFacade {
        val context = activity.applicationContext
        val firebase = FirebaseAuth.getInstance()
        val secure = EncryptedPrefsSecureStorage(context)
        val session = NoopSessionValidator()
        val signInClient = DefaultGoogleSignInClientFacade()
        val executor = ActivityResultGoogleSignInExecutor()
        val tokenRefresher = FirebaseTokenRefresher()
        val signInExecutor = FirebaseSignInExecutor(firebase)
        return FirebaseCredentialAuthFacade(firebase, context, secure, session, signInClient, executor, tokenRefresher, signInExecutor)
    }
}
