package info.lwb.auth

import android.app.Application
import android.content.Context
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import com.google.android.recaptcha.RecaptchaException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RecaptchaTokenProvider {
    suspend fun getToken(action: RecaptchaAction = RecaptchaAction.LOGIN): String?
}

/**
 * Simple in-memory caching decorator to avoid fetching a reCAPTCHA token on every auth attempt.
 * Tokens are action-specific; here we assume LOGIN action reuse is acceptable for short TTL.
 */
class CachingRecaptchaProvider(
    private val delegate: RecaptchaTokenProvider,
    private val ttlMillis: Long = 60_000L,
    private val now: () -> Long = { System.currentTimeMillis() }
) : RecaptchaTokenProvider {
    @Volatile private var cached: String? = null
    @Volatile private var expiry: Long = 0L
    override suspend fun getToken(action: RecaptchaAction): String? {
        val current = now()
        val existing = cached
        if (existing != null && current < expiry) return existing
        val fresh = delegate.getToken(action)
        if (fresh != null) {
            cached = fresh
            expiry = current + ttlMillis
        }
        return fresh
    }
}

class GoogleRecaptchaTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecaptchaTokenProvider {
    @Volatile private var client: RecaptchaClient? = null
    private val siteKey: String = info.lwb.BuildConfig.RECAPTCHA_SITE_KEY

    private suspend fun ensureClient(): RecaptchaClient? {
        client?.let { return it }
        if (siteKey.startsWith("CHANGE_ME")) return null
        return try {
            val app = context.applicationContext as Application
            val c = Recaptcha.fetchClient(app, siteKey) // suspend per beta API
            client = c
            if (info.lwb.BuildConfig.DEBUG) runCatching { android.util.Log.d("Recaptcha", "clientFetched siteKeyPrefix=" + siteKey.take(8)) }
            c
        } catch (e: Exception) {
            if (info.lwb.BuildConfig.DEBUG) runCatching { android.util.Log.w("Recaptcha", "clientFetchFailed ${e.message}") }
            null
        }
    }

    override suspend fun getToken(action: RecaptchaAction): String? = withContext(Dispatchers.IO) {
        val c = ensureClient() ?: return@withContext null
        try {
            val tokenResult = c.execute(action, timeout = 10_000L) // suspend returns Result<String>
            val t = tokenResult.getOrNull()
            if (info.lwb.BuildConfig.DEBUG) {
                val ex = tokenResult.exceptionOrNull()
                if (ex != null) runCatching { android.util.Log.w("Recaptcha", "token failure: ${ex::class.java.simpleName} msg=${ex.message}") }
                runCatching { android.util.Log.d("Recaptcha", "tokenResult null=${t==null} length=${t?.length} action=${action}") }
            }
            t
        } catch (e: Exception) {
            if (info.lwb.BuildConfig.DEBUG) runCatching { android.util.Log.w("Recaptcha", "executeFailed ${e.message}") }
            null
        }
    }
}
