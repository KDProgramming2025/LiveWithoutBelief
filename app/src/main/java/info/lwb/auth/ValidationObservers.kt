package info.lwb.auth

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Logs validation lifecycle events to Logcat (can be swapped for Timber later). */
@Singleton
class LoggingValidationObserver @Inject constructor() : ValidationObserver {
    override fun onAttempt(attempt: Int, max: Int) {
        Log.d(TAG, "attempt=$attempt max=$max")
    }
    override fun onResult(result: ValidationResult) {
        Log.d(TAG, "result valid=${result.isValid} error=${result.error}")
    }
    override fun onRetry(delayMs: Long) {
        Log.d(TAG, "retry delayMs=$delayMs")
    }
    private companion object { const val TAG = "AuthValidate" }
}

/** Fan-out observer that delegates to multiple child observers. */
class CompositeValidationObserver(
    private val delegates: List<ValidationObserver>
) : ValidationObserver {
    override fun onAttempt(attempt: Int, max: Int) = delegates.forEach { runCatching { it.onAttempt(attempt, max) } }
    override fun onResult(result: ValidationResult) = delegates.forEach { runCatching { it.onResult(result) } }
    override fun onRetry(delayMs: Long) = delegates.forEach { runCatching { it.onRetry(delayMs) } }
}

/** Simple in-memory metrics collector (thread-safe) – can be swapped with real analytics. */
@Singleton
class MetricsValidationObserver @Inject constructor() : ValidationObserver {
    @Volatile private var _attempts = 0
    @Volatile private var _success = 0
    @Volatile private var _fail = 0
    @Volatile private var _retries = 0
    override fun onAttempt(attempt: Int, max: Int) { _attempts++ }
    override fun onResult(result: ValidationResult) {
        if (result.isValid) _success++ else _fail++
    }
    override fun onRetry(delayMs: Long) { _retries++ }
    fun snapshot(): Map<String, Int> = mapOf(
        "attempts" to _attempts,
        "success" to _success,
        "fail" to _fail,
        "retries" to _retries,
    )
}

/** Utility to compose observers without creating deep nesting. */
fun ValidationObserver.and(other: ValidationObserver): ValidationObserver = when {
    this === NoopValidationObserver -> other
    other === NoopValidationObserver -> this
    this is CompositeValidationObserver && other is CompositeValidationObserver -> CompositeValidationObserver(this.flatten() + other.flatten())
    this is CompositeValidationObserver -> CompositeValidationObserver(this.flatten() + other)
    other is CompositeValidationObserver -> CompositeValidationObserver(listOf(this) + other.flatten())
    else -> CompositeValidationObserver(listOf(this, other))
}

private fun CompositeValidationObserver.flatten(): List<ValidationObserver> {
    val field = CompositeValidationObserver::class.java.getDeclaredField("delegates")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as List<ValidationObserver>
}

/** Sampling wrapper: forwards events only when random(0..999) < samplePermille (1==0.1%). */
class SamplingValidationObserver(
    private val upstream: ValidationObserver,
    private val samplePermille: Int,
    private val rng: java.util.Random = java.util.Random()
) : ValidationObserver {
    private inline fun sample(block: () -> Unit) {
        val bound = samplePermille.coerceIn(0, 1000)
        if (bound == 1000 || (bound > 0 && rng.nextInt(1000) < bound)) block()
    }
    override fun onAttempt(attempt: Int, max: Int) = sample { upstream.onAttempt(attempt, max) }
    override fun onResult(result: ValidationResult) = sample { upstream.onResult(result) }
    override fun onRetry(delayMs: Long) = sample { upstream.onRetry(delayMs) }
}

/** Export observer that periodically logs snapshot metrics (simple proof-of-concept). */
class SnapshotExportValidationObserver(
    private val metrics: MetricsValidationObserver,
    private val intervalAttempts: Int = 50
) : ValidationObserver {
    @Volatile private var counter = 0
    override fun onAttempt(attempt: Int, max: Int) {
        if (++counter % intervalAttempts == 0) {
            val snap = metrics.snapshot()
            android.util.Log.d("AuthMetrics", "snapshot=${snap}")
        }
    }
    override fun onResult(result: ValidationResult) { /* no-op */ }
    override fun onRetry(delayMs: Long) { /* no-op */ }
}