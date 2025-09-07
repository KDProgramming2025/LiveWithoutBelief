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
    override fun onAttempt(attempt: Int, max: Int) = delegates.forEach { it.onAttempt(attempt, max) }
    override fun onResult(result: ValidationResult) = delegates.forEach { it.onResult(result) }
    override fun onRetry(delayMs: Long) = delegates.forEach { it.onRetry(delayMs) }
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