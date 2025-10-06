package info.lwb.app.logging

import android.util.Log
import info.lwb.core.common.log.AppLogger
import info.lwb.BuildConfig

/** Android implementation of [AppLogger] delegating to `android.util.Log` with a single global tag. */
class AndroidLogger(globalTag: String) : AppLogger {
    private val tag: String = globalTag

    private fun callerFileLine(): String {
        // Skip stack frames until outside this logger class.
        val stack = Exception("capture").stackTrace
        for (el in stack) {
            val cn = el.className
            if (!cn.startsWith(this::class.java.name)) {
                val file = el.fileName ?: cn.substringAfterLast('.')
                val line = el.lineNumber.takeIf { it >= 0 } ?: 0
                return "$file:$line"
            }
        }
        return "?" // Fallback
    }

    private fun format(ref: String, message: String): String {
        val loc = callerFileLine()
        return "[$loc][$ref] $message"
    }

    /** Debug message. */
    override fun d(ref: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, format(ref, msg()))
        }
    }

    /** Info message. */
    override fun i(ref: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, format(ref, msg()))
        }
    }

    /** Warning message. */
    override fun w(ref: String, msg: () -> String, t: Throwable?) {
        if (BuildConfig.DEBUG) {
            if (t != null) {
                Log.w(tag, format(ref, msg()), t)
            } else {
                Log.w(tag, format(ref, msg()))
            }
        }
    }

    /** Error message. */
    override fun e(ref: String, msg: () -> String, t: Throwable?) {
        if (BuildConfig.DEBUG) {
            if (t != null) {
                Log.e(tag, format(ref, msg()), t)
            } else {
                Log.e(tag, format(ref, msg()))
            }
        }
    }

    /** Verbose message. */
    override fun v(ref: String, msg: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, format(ref, msg()))
        }
    }
}
