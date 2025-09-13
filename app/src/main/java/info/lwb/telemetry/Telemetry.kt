/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.telemetry

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Minimal telemetry facade to keep SDKs behind a tiny API. */
object Telemetry {
    @Volatile private var analytics: FirebaseAnalytics? = null
    // Crashlytics/Performance are optional; access via reflection to avoid hard deps
    @Volatile private var crashlytics: Any? = null
    @Volatile private var perf: Any? = null

    fun init(app: Application) {
        // Best-effort init; safe to call multiple times.
        runCatching { analytics = FirebaseAnalytics.getInstance(app) }
        // Optional Crashlytics
        crashlytics = runCatching {
            val cls = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstance = cls.getMethod("getInstance")
            getInstance.invoke(null)
        }.getOrNull()
        // Optional Performance
        perf = runCatching {
            val cls = Class.forName("com.google.firebase.perf.FirebasePerformance")
            val getInstance = cls.getMethod("getInstance")
            getInstance.invoke(null)
        }.getOrNull()
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
        val c = crashlytics ?: return
        runCatching {
            val cls = c.javaClass
            val m = cls.getMethod("recordException", Throwable::class.java)
            m.invoke(c, t)
        }
    }

    fun startTrace(name: String): CloseableTrace? {
        val p = perf ?: return null
        val trace = runCatching {
            val cls = p.javaClass
            val m = cls.getMethod("newTrace", String::class.java)
            m.invoke(p, name.take(32))
        }.getOrNull() ?: return null
        runCatching {
            val m = trace.javaClass.getMethod("start")
            m.invoke(trace)
        }
        return CloseableTrace(trace)
    }
}

class CloseableTrace(private val trace: Any) : AutoCloseable {
    override fun close() {
        runCatching {
            val m = trace.javaClass.getMethod("stop")
            m.invoke(trace)
        }
    }
}
