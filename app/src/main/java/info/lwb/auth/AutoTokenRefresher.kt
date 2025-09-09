package info.lwb.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically checks stored token expiry and triggers a refresh a few minutes before.
 * Simple in-memory scheduler; tie its scope to application lifecycle (e.g., via Hilt @Singleton + ProcessLifecycleOwner scope).
 */
data class TokenRefreshConfig(
    val refreshLeadTimeSeconds: Long = 5 * 60L,
    val pollIntervalSeconds: Long = 60L,
)

class AutoTokenRefresher(
    private val storage: SecureStorage,
    private val authFacade: AuthFacade,
    private val scope: CoroutineScope,
    private val config: TokenRefreshConfig = TokenRefreshConfig(),
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                try {
                    val exp = storage.getTokenExpiry()
                    val now = System.currentTimeMillis() / 1000L
                    if (exp != null && exp - now <= config.refreshLeadTimeSeconds) {
                        authFacade.refreshIdToken(false)
                    }
                } catch (_: Exception) {
                    // swallow; best effort
                }
                delay(config.pollIntervalSeconds.seconds)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
