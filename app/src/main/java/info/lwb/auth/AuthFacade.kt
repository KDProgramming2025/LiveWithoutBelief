/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import info.lwb.BuildConfig
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
    suspend fun register(username: String, password: String, altchaPayload: String?): Result<AuthUser>
    suspend fun passwordLogin(username: String, password: String): Result<AuthUser>
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
    private val registrationApi: RegistrationApi,
    private val passwordApi: PasswordAuthApi,
    private val altcha: AltchaTokenProvider,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
        if (BuildConfig.DEBUG) {
            runCatching {
                android.util.Log.d(
                    "AuthFlow",
                    "oneTapSignIn:start serverClientId=" +
                        BuildConfig.GOOGLE_SERVER_CLIENT_ID.take(12) + "… " +
                        "androidClientId=" +
                        BuildConfig.GOOGLE_ANDROID_CLIENT_ID.take(12) + "…",
                )
            }
        }
        val comp = activity as? ComponentActivity
        // 1. Try silent sign-in (cached GoogleSignIn account)
        val existing = signInClient.getLastSignedInAccount(activity)
        if (BuildConfig.DEBUG) {
            runCatching {
                android.util.Log.d(
                    "AuthFlow",
                    "silentAccount: ${existing?.email} " +
                        "hasToken=${existing?.idToken?.isNotEmpty() == true}",
                )
            }
        }
        val idToken: String = when {
            existing?.idToken != null -> existing.idToken!!
            else -> {
                // 2. Try One Tap (Credential Manager)
                if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "attemptingOneTap") }
                val oneTapToken = oneTapProvider.getIdToken(activity)
                if (BuildConfig.DEBUG) {
                    runCatching {
                        android.util.Log.d(
                            "AuthFlow",
                            "oneTapTokenPresent=${oneTapToken != null}",
                        )
                    }
                }
                if (oneTapToken != null) {
                    oneTapToken
                } else {
                    // 3. Fallback to interactive classic Google Sign-In
                    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "fallbackInteractiveSignIn") }
                    requireNotNull(comp) { "Activity must be a ComponentActivity for interactive sign-in" }
                    val acct = intentExecutor.launch(comp) { signInClient.buildSignInIntent(activity) }
                    if (BuildConfig.DEBUG) {
                        runCatching {
                            android.util.Log.d(
                                "AuthFlow",
                                "interactiveResult email=${acct.email} " +
                                    "hasToken=${acct.idToken != null}",
                            )
                        }
                    }
                    acct.idToken ?: error("Missing idToken from interactive sign-in")
                }
            }
        }
        if (BuildConfig.DEBUG) {
            runCatching {
                android.util.Log.d("AuthFlow", "haveIdToken length=${idToken.length}")
            }
        }
        // Persist the Google ID token locally for potential future use (Firebase refresh etc.).
        secureStorage.putIdToken(idToken)
        // Cache token expiry for future refresh heuristics
        val exp = JwtUtils.extractExpiryEpochSeconds(idToken)
        val storeExp = when {
            exp == null -> (System.currentTimeMillis() / 1000L) + (50 * 60)
            else -> exp - 5 * 60
        }
        secureStorage.putTokenExpiry(storeExp)
        // Sign into Firebase to obtain a reliable email, then register/upsert by email on backend.
        val user = signInExecutor.signIn(idToken)
        val email = user.email ?: existing?.email ?: error("Could not determine Google account email")
        val (regUser, created) = registrationApi.register(email)
        val authUser = AuthUser(user.uid, user.displayName ?: regUser.displayName, email, user.photoUrl?.toString())
        secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
        // Optionally refresh a Firebase token for Firebase-internal use, but avoid storing it as the server token
        runCatching { tokenRefresher.refresh(user, false) }.onFailure {
            if (BuildConfig.DEBUG) android.util.Log.w("AuthFlow", "tokenRefreshFailed", it)
        }
        if (BuildConfig.DEBUG) {
            runCatching {
                android.util.Log.d(
                    "AuthFlow",
                    "oneTapSignIn:success uid=${authUser.uid}",
                )
            }
        }
        authUser
    }.recoverCatching { e ->
        val rb = if (e.isRegionBlocked()) {
            RegionBlockedAuthException(
                "Google sign-in appears blocked in your region.",
                e,
            )
        } else {
            null
        }
        if (BuildConfig.DEBUG) {
            runCatching {
                if (rb != null) {
                    android.util.Log.e("AuthFlow", "oneTapSignIn:failure(region-block)", rb)
                } else {
                    android.util.Log.e("AuthFlow", "oneTapSignIn:failure", e)
                }
            }
        }
        // Rethrow either wrapped or original to keep a failure Result without crashing caller
        throw (rb ?: e)
    }

    override fun currentUser(): AuthUser? {
        val fb = firebaseAuth.currentUser
        if (fb != null) return AuthUser(fb.uid, fb.displayName, fb.email, fb.photoUrl?.toString())
        // Fallback to stored token/profile (for password login or when Firebase user is absent)
        val token = secureStorage.getIdToken() ?: return null
        val sub = JwtUtils.extractSubject(token) ?: return null
        val (name, email, avatar) = secureStorage.getProfile()
        return AuthUser(sub, name, email, avatar)
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

    override suspend fun register(username: String, password: String, altchaPayload: String?): Result<AuthUser> =
        runCatching {
            val token = altchaPayload ?: throw IllegalStateException("ALTCHA required")
            val user = passwordApi.register(username, password, token)
            secureStorage.putIdToken("pwd:${user.uid}")
            secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
            user
        }

    override suspend fun passwordLogin(username: String, password: String): Result<AuthUser> = runCatching {
        val user = passwordApi.login(username, password)
        secureStorage.putIdToken("pwd:${user.uid}")
        secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
        user
    }
}

class RegionBlockedAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class EmailLinkInitiatedException(
    val email: String,
    cause: Throwable? = null,
) : RuntimeException("Email link sent to $email", cause)

private fun Throwable.isRegionBlocked(): Boolean {
    var cur: Throwable? = this
    while (cur != null) {
        val msg = cur.message?.lowercase() ?: ""
        if (msg.contains("403") && msg.contains("forbidden")) return true
        if (msg.contains("verifyassertion") && msg.contains("permission")) return true
        cur = cur.cause
    }
    return false
}

// Email link and password auth removed in favor of Google Sign-In only.

// Test helper (internal so accessible from unit tests in same module)
internal fun testIsRegionBlocked(t: Throwable): Boolean = t.isRegionBlocked()
