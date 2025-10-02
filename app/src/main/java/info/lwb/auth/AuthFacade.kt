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

internal const val TOKEN_EARLY_REFRESH_SECONDS = 5 * 60
internal const val TOKEN_FALLBACK_LIFETIME_SECONDS = 50 * 60

/**
 * Resolved authenticated user model used by the app layer.
 * @property uid Stable unique user id (Firebase UID or password namespace pseudo id).
 * @property displayName User chosen or server provided display name.
 * @property email Primary email associated with the account (nullable if not provided).
 * @property photoUrl Optional avatar image URL.
 */
data class AuthUser(val uid: String, val displayName: String?, val email: String?, val photoUrl: String?)

/**
 * High level authentication entry point hiding concrete providers (Google One Tap / Firebase / Password).
 */
interface AuthFacade {
    /** Initiates silent + one-tap + interactive fallback Google sign-in flow returning an [AuthUser]. */
    suspend fun oneTapSignIn(activity: Activity): Result<AuthUser>

    /** Returns currently resolved user or null (prefers live Firebase user, falls back to persisted profile). */
    fun currentUser(): AuthUser?

    /** Clears remote session (if token present) and local persisted credentials. */
    suspend fun signOut()

    /** Refreshes (optionally force) an ID token and persists updated expiry. */
    suspend fun refreshIdToken(forceRefresh: Boolean = false): Result<String>

    /** Registers a password based account (ALTCHA required) and persists profile. */
    suspend fun register(username: String, password: String, altchaPayload: String?): Result<AuthUser>

    /** Performs password based login and persists resulting profile. */
    suspend fun passwordLogin(username: String, password: String): Result<AuthUser>
}

/** Optional abstraction for One Tap / Credential Manager based Google ID token retrieval. */

/**
 * Abstraction over Firebase token refresh so unit tests can mock without dealing with Task APIs.
 */
interface TokenRefresher {
    /** Refreshes a Firebase ID token, optionally forcing network. Returns raw token string. */
    suspend fun refresh(user: com.google.firebase.auth.FirebaseUser, force: Boolean): String
}

/** Concrete [TokenRefresher] using Firebase SDK Tasks. */
class FirebaseTokenRefresher @javax.inject.Inject constructor() : TokenRefresher {
    override suspend fun refresh(user: com.google.firebase.auth.FirebaseUser, force: Boolean): String =
        user.getIdToken(force).await().token ?: error("Token refresh returned null")
}

/**
 * Executes a Firebase sign-in with a Google ID token credential.
 * Abstracted to enable testing without invoking the real FirebaseAuth credential flow.
 */
interface SignInExecutor {
    /** Signs into Firebase using a Google ID token credential returning the created / existing user. */
    suspend fun signIn(idToken: String): com.google.firebase.auth.FirebaseUser
}

/** Default Firebase-backed [SignInExecutor]. */
class FirebaseSignInExecutor @javax.inject.Inject constructor(private val firebaseAuth: FirebaseAuth) : SignInExecutor {
    override suspend fun signIn(idToken: String): com.google.firebase.auth.FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebaseAuth
            .signInWithCredential(credential)
            .await()
            .user ?: error("Firebase user null after sign in")
    }
}

/**
 * Production implementation of [AuthFacade] combining Google identity, Firebase, and backend registration.
 */
class FirebaseCredentialAuthFacade @javax.inject.Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val sessionValidator: SessionValidator,
    private val identityFacade: IdentityCredentialFacade,
    private val tokenRefresher: TokenRefresher,
    private val signInExecutor: SignInExecutor,
    private val registrationApi: RegistrationApi,
    private val passwordApi: PasswordAuthApi,
    private val altcha: AltchaTokenProvider,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> =
        runCatching { oneTapSignInInternal(activity) }
            .recoverCatching { e -> throw mapRegionBlock(e) }

    override fun currentUser(): AuthUser? {
        val live = firebaseAuth.currentUser
        if (live != null) {
            return AuthUser(live.uid, live.displayName, live.email, live.photoUrl?.toString())
        }
        val token = secureStorage.getIdToken()
        val subject = token?.let { JwtUtils.extractSubject(it) }
        return if (subject == null) {
            null
        } else {
            val (name, email, avatar) = secureStorage.getProfile()
            AuthUser(subject, name, email, avatar)
        }
    }

    override suspend fun signOut() {
        val token = secureStorage.getIdToken()
        runCatching {
            if (token != null) {
                sessionValidator.revoke(token)
            }
        }
        firebaseAuth.signOut()
        secureStorage.clear()
    }

    override suspend fun refreshIdToken(forceRefresh: Boolean): Result<String> =
        runCatching { refreshToken(forceRefresh) }

    override suspend fun register(username: String, password: String, altchaPayload: String?): Result<AuthUser> =
        runCatching { registerInternal(username, password, altchaPayload) }

    override suspend fun passwordLogin(username: String, password: String): Result<AuthUser> =
        runCatching { passwordLoginInternal(username, password) }

    // region in-class helpers
    private suspend fun oneTapSignInInternal(activity: Activity): AuthUser {
        logDebugStart()
        val comp = activity as? ComponentActivity ?: error("Activity must be ComponentActivity")
        val tokenResult = identityFacade.getIdToken(comp)
        val idToken = tokenResult.idToken
        logDebugHaveToken(idToken)
        persistGoogleToken(idToken)
        val user = signInExecutor.signIn(idToken)
        val email = user.email ?: tokenResult.email ?: error("Could not determine Google account email")
        val (regUser, _) = registrationApi.register(email)
        val authUser = AuthUser(
            user.uid,
            user.displayName ?: regUser.displayName,
            email,
            user.photoUrl?.toString(),
        )
        secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
        runCatching { tokenRefresher.refresh(user, false) }.onFailure { err ->
            if (BuildConfig.DEBUG) {
                android.util.Log.w("AuthFlow", "tokenRefreshFailed", err)
            }
        }
        logDebugSuccess(authUser)
        return authUser
    }

    private suspend fun registerInternal(
        username: String,
        password: String,
        altchaPayload: String?,
    ): AuthUser {
        val token = altchaPayload ?: throw IllegalStateException("ALTCHA required")
        val user = passwordApi.register(username, password, token)
        secureStorage.putIdToken("pwd:${'$'}{user.uid}")
        secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
        return user
    }

    private suspend fun passwordLoginInternal(username: String, password: String): AuthUser {
        val user = passwordApi.login(username, password)
        secureStorage.putIdToken("pwd:${'$'}{user.uid}")
        secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
        return user
    }

    private suspend fun refreshToken(forceRefresh: Boolean): String {
        val user = firebaseAuth.currentUser ?: error("No user to refresh")
        val token = tokenRefresher.refresh(user, forceRefresh)
        persistExpiry(token)
        return token
    }

    // Legacy GoogleSignIn flow removed after migration to Credential Manager.

    private fun persistGoogleToken(idToken: String) {
        secureStorage.putIdToken(idToken)
        persistExpiry(idToken)
    }

    private fun persistExpiry(token: String) {
        val exp = JwtUtils.extractExpiryEpochSeconds(token)
        val nowSeconds = System.currentTimeMillis() / MILLIS_IN_SECOND
        val early = exp?.minus(TOKEN_EARLY_REFRESH_SECONDS)
        val storeExp = early ?: (nowSeconds + TOKEN_FALLBACK_LIFETIME_SECONDS)
        secureStorage.putTokenExpiry(storeExp)
    }

    private fun mapRegionBlock(e: Throwable): Throwable {
        val mapped = if (e.isRegionBlocked()) {
            RegionBlockedAuthException("Google sign-in appears blocked in your region.", e)
        } else {
            null
        }
        logDebugFailure(mapped, e)
        return mapped ?: e
    }
    // endregion
}

// logging helpers moved to AuthLogging.kt

/** Indicates Google sign-in is blocked (likely by region restrictions). */
class RegionBlockedAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Email link sign-in flow was initiated.
 * @property email Address the sign-in link was sent to.
 * @param cause Optional underlying cause (e.g. network error) that triggered initiating link sign-in.
 */
class EmailLinkInitiatedException(val email: String, cause: Throwable? = null) :
    RuntimeException("Email link sent to $email", cause)

private fun Throwable.isRegionBlocked(): Boolean {
    var cur: Throwable? = this
    while (cur != null) {
        val msg = cur.message?.lowercase().orEmpty()
        if (msg.isRegionBlockMessage()) {
            return true
        }
        cur = cur.cause
    }
    return false
}

private fun String.isRegionBlockMessage(): Boolean =
    (contains("403") && contains("forbidden")) || (contains("verifyassertion") && contains("permission"))

// Email link and password auth removed in favor of Google Sign-In only.

// Test helper (internal so accessible from unit tests in same module)
internal fun testIsRegionBlocked(t: Throwable): Boolean = t.isRegionBlocked()

private const val MILLIS_IN_SECOND = 1000L
