/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth

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
        return FirebaseCredentialAuthFacade(
            firebase,
            context,
            secure,
            session,
            signInClient,
            executor,
            tokenRefresher,
            signInExecutor,
        )
    }
}
