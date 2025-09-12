/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.telemetry

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/** Minimal telemetry facade to keep SDKs behind a tiny API. */
object Telemetry {
    @Volatile private var analytics: FirebaseAnalytics? = null

    @Volatile private var crashlytics: FirebaseCrashlytics? = null

    @Volatile private var perf: FirebasePerformance? = null

    fun init(app: Application) {
        // Best-effort init; safe to call multiple times.
        runCatching { analytics = FirebaseAnalytics.getInstance(app) }
        runCatching { crashlytics = FirebaseCrashlytics.getInstance() }
        runCatching { perf = FirebasePerformance.getInstance() }
    }

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val fa = analytics ?: return
        val bundle = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> {}
                is String -> bundle.putString(k, v.take(36)) // avoid long PII
                is Int -> bundle.putInt(k, v)
                is Long -> bundle.putLong(k, v)
                is Double -> bundle.putDouble(k, v)
                is Boolean -> bundle.putString(k, if (v) "1" else "0")
                else -> bundle.putString(k, v.toString().take(36))
            }
        }
        runCatching { fa.logEvent(name.take(32), bundle) }
    }

    fun recordCaught(t: Throwable) {
        crashlytics?.recordException(t)
    }

    fun startTrace(name: String): CloseableTrace? {
        val p = perf ?: return null
        val trace = runCatching { p.newTrace(name.take(32)) }.getOrNull() ?: return null
        trace.start()
        return CloseableTrace(trace)
    }
}

class CloseableTrace(private val trace: Trace) : AutoCloseable {
    override fun close() {
        runCatching { trace.stop() }
    }
}
