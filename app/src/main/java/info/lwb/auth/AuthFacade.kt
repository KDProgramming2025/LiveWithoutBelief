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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import info.lwb.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.MessageDigest
import android.util.Base64

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
    suspend fun register(username: String, password: String, recaptchaToken: String? = null): Result<AuthUser>
    suspend fun passwordLogin(username: String, password: String, recaptchaToken: String? = null): Result<AuthUser>
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
    @AuthClient private val http: okhttp3.OkHttpClient,
    @AuthBaseUrl private val authBaseUrl: String,
) : AuthFacade {
    override suspend fun oneTapSignIn(activity: Activity): Result<AuthUser> = runCatching {
    if (BuildConfig.DEBUG) runCatching { android.util.Log.d(
            "AuthFlow",
            "oneTapSignIn:start serverClientId=${BuildConfig.GOOGLE_SERVER_CLIENT_ID.take(12)}… androidClientId=${BuildConfig.GOOGLE_ANDROID_CLIENT_ID.take(12)}…"
    ) }
        val comp = activity as? ComponentActivity
        // 1. Try silent sign-in (cached GoogleSignIn account)
        val existing = signInClient.getLastSignedInAccount(activity)
    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "silentAccount: ${existing?.email} hasToken=${existing?.idToken?.isNotEmpty()==true}") }
        val idToken: String = when {
            existing?.idToken != null -> existing.idToken!!
            else -> {
                // 2. Try One Tap (Credential Manager)
                if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "attemptingOneTap") }
                val oneTapToken = oneTapProvider.getIdToken(activity)
                if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "oneTapTokenPresent=${oneTapToken!=null}") }
                if (oneTapToken != null) oneTapToken else {
                    // 3. Fallback to interactive classic Google Sign-In
                    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "fallbackInteractiveSignIn") }
                    requireNotNull(comp) { "Activity must be a ComponentActivity for interactive sign-in" }
                    val acct = intentExecutor.launch(comp) { signInClient.buildSignInIntent(activity) }
                    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "interactiveResult email=${acct.email} hasToken=${acct.idToken!=null}") }
                    acct.idToken ?: error("Missing idToken from interactive sign-in")
                }
            }
        }
    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "haveIdToken length=${idToken.length}") }
        val user = signInExecutor.signIn(idToken)
        val authUser = AuthUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
        secureStorage.putProfile(authUser.displayName, authUser.email, authUser.photoUrl)
        runCatching { tokenRefresher.refresh(user, false) }.onFailure {
            if (BuildConfig.DEBUG) android.util.Log.w("AuthFlow", "tokenRefreshFailed", it)
        }.getOrNull()?.let { token ->
        if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "refreshedIdToken length=${token.length}") }
            secureStorage.putIdToken(token)
            sessionValidator.validate(token)
        }
    if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "oneTapSignIn:success uid=${authUser.uid}") }
        authUser
    }.recoverCatching { e ->
        val rb = if (e.isRegionBlocked()) RegionBlockedAuthException("Google sign-in appears blocked in your region.", e) else null
        if (BuildConfig.DEBUG) runCatching {
            if (rb != null) android.util.Log.e("AuthFlow", "oneTapSignIn:failure(region-block)", rb) else android.util.Log.e("AuthFlow", "oneTapSignIn:failure", e)
        }
        // Rethrow either wrapped or original to keep a failure Result without crashing caller
        throw (rb ?: e)
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

    @Serializable private data class PasswordAuthRequest(val username: String, val password: String, val altcha: String? = null)

    private suspend fun passwordAuthRequest(path: String, username: String, password: String, recaptchaToken: String?): Result<Pair<String, AuthUser>> = runCatching {
        val base = authBaseUrl.trimEnd('/')
        val altchaTokenOrNull = if (path.endsWith("/register") && (recaptchaToken == null || recaptchaToken.isBlank())) {
            // No reCAPTCHA token (blocked region or disabled). Fetch and solve ALTCHA.
            if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "altcha:fetch+solve starting") }
            fetchAndSolveAltcha(base)
        } else recaptchaToken
        val payload = PasswordAuthRequest(username = username, password = password, altcha = altchaTokenOrNull)
        val json = Json.encodeToString(payload)
        if (BuildConfig.DEBUG) runCatching {
            android.util.Log.d(
                "AuthFlow",
                "pwdAuth payloadSize=${json.length} path=$path hasAltcha=${payload.altcha!=null}"
            )
        }
        val body = json.toRequestBody("application/json".toMediaType())
        val req = okhttp3.Request.Builder()
            .url(base + path)
            .post(body)
            .build()
        val resp = try {
            // Offload blocking I/O to avoid NetworkOnMainThreadException
            withContext(Dispatchers.IO) { http.newCall(req).execute() }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { android.util.Log.w("AuthFlow", "pwdAuth EXC path=$path msg=${e.message}", e) }
            throw e
        }
        resp.use { r ->
            if (!r.isSuccessful) {
                val code = r.code
                val errBody = runCatching { r.body?.string() }.getOrNull()
                if (BuildConfig.DEBUG) runCatching {
                    android.util.Log.w(
                        "AuthFlow",
                        "pwdAuth FAIL path=$path code=$code body=${errBody?.take(300)}"
                    )
                }
                var serverErr: String? = null
                if (errBody != null && errBody.length < 4096) {
                    val trimmed = errBody.trim()
                    if (trimmed.startsWith("{")) {
                        // Try naive extraction to avoid allocating a whole model
                        Regex("\\\"error\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(trimmed)?.let { m -> serverErr = m.groupValues[1] }
                    }
                }
                val msg = when (serverErr) {
                    "recaptcha_failed" -> "reCAPTCHA verification failed. Please retry."
                    "username_exists" -> "Username already taken"
                    "invalid_credentials" -> "Invalid username or password"
                    "invalid_body" -> "Invalid input"
                    else -> when(code) {
                        409 -> "Username already taken"
                        401 -> "Invalid username or password"
                        400 -> serverErr ?: "Invalid input"
                        else -> serverErr ?: "Server error ($code)"
                    }
                }
                error(msg)
            }
            val str = r.body?.string() ?: error("Empty body")
            if (BuildConfig.DEBUG) runCatching {
                android.util.Log.d("AuthFlow", "pwdAuth OK path=$path bodySize=${str.length}")
            }
            @Serializable data class UserPayload(val id: String, val username: String? = null)
            @Serializable data class AuthResponse(val token: String, val user: UserPayload)
            val parsed = Json.decodeFromString<AuthResponse>(str)
            val authUser = AuthUser(parsed.user.id, parsed.user.username, null, null)
            parsed.token to authUser
        }
    }

    @Serializable private data class AltchaChallenge(
        val algorithm: String,
        val challenge: String,
        val salt: String,
        val signature: String,
        val maxnumber: Long? = null,
    )

    @Serializable private data class AltchaPayload(
        val algorithm: String,
        val challenge: String,
        val number: Long,
        val salt: String,
        val signature: String,
    )

    private suspend fun fetchAndSolveAltcha(baseUrl: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v1/altcha/challenge"
        val req = okhttp3.Request.Builder().url(url).get().build()
        val resp = http.newCall(req).execute()
        resp.use { r ->
            if (!r.isSuccessful) error("ALTCHA challenge fetch failed (${r.code})")
            val body = r.body?.string() ?: error("Empty ALTCHA challenge body")
            val json = Json { ignoreUnknownKeys = true }
            val ch = json.decodeFromString<AltchaChallenge>(body)
            val max = (ch.maxnumber ?: 100_000L).coerceAtMost(1_000_000L)
            val number = withContext(Dispatchers.Default) { solveAltcha(ch.algorithm, ch.challenge, ch.salt, max) }
            if (BuildConfig.DEBUG) runCatching { android.util.Log.d("AuthFlow", "altcha:solved number=$number max=$max") }
            val payload = AltchaPayload(ch.algorithm, ch.challenge, number, ch.salt, ch.signature)
            val payloadJson = Json.encodeToString(payload)
            Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun solveAltcha(algorithm: String, challengeHex: String, salt: String, max: Long): Long {
        val algo = when (algorithm.uppercase()) {
            "SHA-1" -> "SHA-1"
            "SHA-512" -> "SHA-512"
            else -> "SHA-256"
        }
        val md = MessageDigest.getInstance(algo)
        for (n in 0L..max) {
            md.reset()
            md.update((salt + n.toString()).toByteArray(Charsets.UTF_8))
            val h = md.digest().toHexLower()
            if (h == challengeHex) return n
        }
        error("ALTCHA solution not found up to max=$max")
    }

    private fun ByteArray.toHexLower(): String = joinToString(separator = "") { b ->
        val i = b.toInt() and 0xFF
        val hi = "0123456789abcdef"[i ushr 4]
        val lo = "0123456789abcdef"[i and 0x0F]
        "$hi$lo"
    }

    override suspend fun register(username: String, password: String, recaptchaToken: String?): Result<AuthUser> =
        passwordAuthRequest("/v1/auth/register", username, password, recaptchaToken).map { (token, user) ->
            secureStorage.putIdToken(token)
            secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
            sessionValidator.validate(token)
            user
        }

    override suspend fun passwordLogin(username: String, password: String, recaptchaToken: String?): Result<AuthUser> =
        passwordAuthRequest("/v1/auth/login", username, password, recaptchaToken).map { (token, user) ->
            secureStorage.putIdToken(token)
            secureStorage.putProfile(user.displayName, user.email, user.photoUrl)
            sessionValidator.validate(token)
            user
        }
}

class RegionBlockedAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class EmailLinkInitiatedException(val email: String, cause: Throwable? = null) : RuntimeException("Email link sent to $email", cause)

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

// Email link auth removed; password auth will be implemented separately.

// Test helper (internal so accessible from unit tests in same module)
internal fun testIsRegionBlocked(t: Throwable): Boolean = t.isRegionBlocked()