/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
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
    /** Return last signed in account if cached, else null. */
    fun getLastSignedInAccount(activity: Activity): GoogleSignInAccount?

    /** Build an intent to start interactive Google Sign-In. */
    fun buildSignInIntent(activity: Activity): Intent
}

/** Default production implementation backed by Google Play Services.
 *  Validates configuration and builds the sign-in intent requesting ID token + email.
 */
class DefaultGoogleSignInClientFacade : GoogleSignInClientFacade {
    /** Retrieve the last signed in account if still cached on device. */
    override fun getLastSignedInAccount(activity: Activity): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(activity)

    /** Build a sign-in intent using the configured server client id (web client id from Firebase).
     *  Fails fast in debug if the id is unset or placeholder to avoid silent auth issues.
     */
    override fun buildSignInIntent(activity: Activity): Intent {
        // IMPORTANT: requestIdToken must use the WEB (server) client id from Firebase (client_type=3)
        // for FirebaseAuth.signInWithCredential
        val serverId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        check(serverId.isNotBlank() && !serverId.startsWith("CHANGE_ME")) {
            "GOOGLE_SERVER_CLIENT_ID is not configured correctly"
        }
        val gso = GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverId)
            .requestEmail()
            .build()
        if (BuildConfig.DEBUG) {
            val serverPreview = BuildConfig.GOOGLE_SERVER_CLIENT_ID.take(ID_PREVIEW_LEN)
            val androidPreview = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.take(ID_PREVIEW_LEN)
            android.util.Log
                .d(
                    "AuthFlow",
                    "GoogleSignInOptions serverId=$serverPreview… androidId=$androidPreview…",
                )
        }
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }
}

/** Executor abstraction for launching the interactive sign-in and returning the account. */
interface GoogleSignInIntentExecutor {
    /** Launch sign-in via provided intent provider and return resulting account or throw on failure. */
    suspend fun launch(activity: ComponentActivity, intentProvider: () -> Intent): GoogleSignInAccount
}

/** ActivityResult-backed executor that launches the intent and returns the account or fails.
 *  Each invocation registers a one-off launcher (unique key) and cleans up automatically.
 */
class ActivityResultGoogleSignInExecutor : GoogleSignInIntentExecutor {
    override suspend fun launch(activity: ComponentActivity, intentProvider: () -> Intent): GoogleSignInAccount =
        suspendCancellableCoroutine { cont ->
            val key = "google-sign-in-${System.nanoTime()}"
            val launcher = activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val signInResult = runCatching {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    task.getResult(ApiException::class.java)
                }
                signInResult
                    .onSuccess { acc ->
                        cont.resume(acc)
                    }.onFailure { t ->
                        val api = t as? ApiException
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e(
                                "AuthFlow",
                                "googleSignIn failure status=${api?.statusCode}",
                                t,
                            )
                        }
                        cont.resumeWith(Result.failure(RuntimeException("Sign-in failed", t)))
                    }
            }
            launcher.launch(intentProvider())
        }
}

//region internal constants
private const val ID_PREVIEW_LEN = 18
//endregion
