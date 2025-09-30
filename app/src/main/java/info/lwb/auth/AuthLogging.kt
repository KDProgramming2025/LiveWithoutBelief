/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.auth

import info.lwb.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

private const val AUTH_LOG_TAG = "AuthFlow"
private const val CLIENT_ID_SAMPLE = 12

// region logging helpers (no-op in release) extracted from AuthFacade to lower function count and isolate logging.
private inline fun logDebug(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) {
        runCatching { android.util.Log.d(tag, msg()) }
    }
}

internal fun FirebaseCredentialAuthFacade.logDebugStart() = logDebug(AUTH_LOG_TAG) {
    val serverPart = BuildConfig.GOOGLE_SERVER_CLIENT_ID.take(CLIENT_ID_SAMPLE)
    val androidPart = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.take(CLIENT_ID_SAMPLE)
    "oneTapSignIn:start serverClientId=$serverPart… androidClientId=$androidPart…"
}

internal fun FirebaseCredentialAuthFacade.logDebugSilent(existing: GoogleSignInAccount?) = logDebug(AUTH_LOG_TAG) {
    "silentAccount: ${existing?.email} hasToken=${existing?.idToken?.isNotEmpty() == true}"
}

internal fun FirebaseCredentialAuthFacade.logDebugHaveToken(idToken: String) = logDebug(AUTH_LOG_TAG) {
    "haveIdToken length=${idToken.length}"
}

internal fun FirebaseCredentialAuthFacade.logDebugSuccess(user: AuthUser) = logDebug(AUTH_LOG_TAG) {
    "oneTapSignIn:success uid=${user.uid}"
}

internal fun logDebugFailure(mapped: Throwable?, original: Throwable) {
    if (BuildConfig.DEBUG) {
        runCatching {
            if (mapped != null) {
                android.util.Log.e(AUTH_LOG_TAG, "oneTapSignIn:failure(region-block)", mapped)
            } else {
                android.util.Log.e(AUTH_LOG_TAG, "oneTapSignIn:failure", original)
            }
        }
    }
}
// endregion
