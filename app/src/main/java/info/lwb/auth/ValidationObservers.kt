/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

// region internal constants
private const val PERMILLE_MIN = 0
private const val PERMILLE_MAX = 1000
// Using a human readable denominator name clarifies the intent of sampling logic.
// endregion

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

    private companion object {
        const val TAG = "AuthValidate"
    }
}

/**
 * Fan-out observer that delegates to multiple child observers.
 * Exposes its delegate list via [children] so composition utilities avoid reflection & unsafe casts.
 */
class CompositeValidationObserver(internal val children: List<ValidationObserver>) : ValidationObserver {
    override fun onAttempt(attempt: Int, max: Int) {
        children.forEach {
            runCatching { it.onAttempt(attempt, max) }
        }
    }

    override fun onResult(result: ValidationResult) {
        children.forEach {
            runCatching { it.onResult(result) }
        }
    }

    override fun onRetry(delayMs: Long) {
        children.forEach {
            runCatching { it.onRetry(delayMs) }
        }
    }
}

/** Simple in-memory metrics collector (thread-safe) – can be swapped with real analytics. */
@Singleton
class MetricsValidationObserver @Inject constructor() : ValidationObserver {
    @Volatile private var attemptsCount = 0

    @Volatile private var successCount = 0

    @Volatile private var failCount = 0

    @Volatile private var retriesCount = 0

    override fun onAttempt(attempt: Int, max: Int) {
        attemptsCount++
    }

    override fun onResult(result: ValidationResult) {
        if (result.isValid) {
            successCount++
        } else {
            failCount++
        }
    }

    override fun onRetry(delayMs: Long) {
        retriesCount++
    }

    /**
     * Returns a point-in-time snapshot of collected counters. Each key maps to the cumulative
     * number of events since process start. Safe for concurrent invocation because the counters
     * are stored in volatile fields and updates are atomic increments relative to visibility needs.
     */
    fun snapshot(): Map<String, Int> = mapOf(
        "attempts" to attemptsCount,
        "success" to successCount,
        "fail" to failCount,
        "retries" to retriesCount,
    )
}

/** Utility to compose observers without creating deep nesting. */
fun ValidationObserver.and(other: ValidationObserver): ValidationObserver = when {
    this === NoopValidationObserver -> {
        other
    }
    other === NoopValidationObserver -> {
        this
    }
    this is CompositeValidationObserver && other is CompositeValidationObserver -> {
        CompositeValidationObserver(this.children + other.children)
    }
    this is CompositeValidationObserver -> {
        CompositeValidationObserver(this.children + other)
    }
    other is CompositeValidationObserver -> {
        CompositeValidationObserver(listOf(this) + other.children)
    }
    else -> {
        CompositeValidationObserver(listOf(this, other))
    }
}

/** Sampling wrapper: forwards events only when random(0..999) < samplePermille (1==0.1%). */
class SamplingValidationObserver(
    private val upstream: ValidationObserver,
    private val samplePermille: Int,
    private val rng: java.util.Random = java.util.Random(),
) : ValidationObserver {
    private inline fun sample(block: () -> Unit) {
        val bound = samplePermille.coerceIn(PERMILLE_MIN, PERMILLE_MAX)
        if (bound == PERMILLE_MAX || (bound > PERMILLE_MIN && rng.nextInt(PERMILLE_MAX) < bound)) {
            block()
        }
    }

    override fun onAttempt(attempt: Int, max: Int) {
        sample { upstream.onAttempt(attempt, max) }
    }

    override fun onResult(result: ValidationResult) {
        sample { upstream.onResult(result) }
    }

    override fun onRetry(delayMs: Long) {
        sample { upstream.onRetry(delayMs) }
    }
}

/** Export observer that periodically logs snapshot metrics (simple proof-of-concept). */
class SnapshotExportValidationObserver(
    private val metrics: MetricsValidationObserver,
    private val intervalAttempts: Int = 50,
) : ValidationObserver {
    @Volatile private var counter = 0

    override fun onAttempt(attempt: Int, max: Int) {
        if (++counter % intervalAttempts == 0) {
            val snap = metrics.snapshot()
            android.util.Log.d("AuthMetrics", "snapshot=$snap")
        }
    }

    override fun onResult(result: ValidationResult) { /* no-op */ }

    override fun onRetry(delayMs: Long) { /* no-op */ }
}
