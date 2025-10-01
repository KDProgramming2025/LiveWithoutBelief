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

    private const val MAX_PARAM_VALUE_LEN = 36
    private const val MAX_EVENT_NAME_LEN = 32
    private const val CLASS_CRASHLYTICS = "com.google.firebase.crashlytics.FirebaseCrashlytics"
    private const val CLASS_PERF = "com.google.firebase.perf.FirebasePerformance"
    private const val METHOD_GET_INSTANCE = "getInstance"
    private const val METHOD_RECORD_EXCEPTION = "recordException"
    private const val METHOD_NEW_TRACE = "newTrace"
    private const val METHOD_START = "start"
    internal const val METHOD_STOP = "stop"

    /**
     * Initialize telemetry backends (analytics + optional crash/perf) in a best-effort way.
     * Safe to invoke multiple times; later calls are cheap.
     */
    fun init(app: Application) {
        runCatching { analytics = FirebaseAnalytics.getInstance(app) }
        crashlytics = runCatching { reflectGetInstance(CLASS_CRASHLYTICS) }.getOrNull()
        perf = runCatching { reflectGetInstance(CLASS_PERF) }.getOrNull()
    }

    /** Log an analytics event with optional (primitive/string) parameters. */
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val fa = analytics ?: return
        val bundle = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> { /* skip */ }
                is String -> {
                    bundle.putString(k, v.take(MAX_PARAM_VALUE_LEN))
                }
                is Int -> {
                    bundle.putInt(k, v)
                }
                is Long -> {
                    bundle.putLong(k, v)
                }
                is Double -> {
                    bundle.putDouble(k, v)
                }
                is Boolean -> {
                    bundle.putString(
                        k,
                        if (v) {
                            "1"
                        } else {
                            "0"
                        },
                    )
                }
                else -> {
                    bundle.putString(k, v.toString().take(MAX_PARAM_VALUE_LEN))
                }
            }
        }
        runCatching { fa.logEvent(name.take(MAX_EVENT_NAME_LEN), bundle) }
    }

    /** Record a caught (non-fatal) exception if Crashlytics is available. */
    fun recordCaught(t: Throwable) {
        val c = crashlytics ?: return
        runCatching {
            val cls = c.javaClass
            val m = cls.getMethod(METHOD_RECORD_EXCEPTION, Throwable::class.java)
            m.invoke(c, t)
        }
    }

    /** Start a performance trace returning a closeable handle, or null if perf SDK missing. */
    fun startTrace(name: String): CloseableTrace? {
        val p = perf ?: return null
        val trace = runCatching {
            val cls = p.javaClass
            val m = cls.getMethod(METHOD_NEW_TRACE, String::class.java)
            m.invoke(p, name.take(MAX_EVENT_NAME_LEN))
        }.getOrNull() ?: return null
        runCatching {
            val m = trace.javaClass.getMethod(METHOD_START)
            m.invoke(trace)
        }
        return CloseableTrace(trace)
    }

    private fun reflectGetInstance(className: String): Any? {
        val cls = Class.forName(className)
        val getInstance = cls.getMethod(METHOD_GET_INSTANCE)
        return getInstance.invoke(null)
    }
}

/** Wrapper to stop a performance trace via [AutoCloseable]. */
class CloseableTrace(private val trace: Any) : AutoCloseable {
    override fun close() {
        runCatching {
            val m = trace.javaClass.getMethod(Telemetry.METHOD_STOP)
            m.invoke(trace)
        }
    }
}
