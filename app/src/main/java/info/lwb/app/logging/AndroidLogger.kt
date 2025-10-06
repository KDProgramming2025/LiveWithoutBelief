package info.lwb.app.logging

import android.util.Log
import info.lwb.core.common.log.AppLogger
import info.lwb.BuildConfig

/** Android implementation of [AppLogger] delegating to `android.util.Log` with a single global tag. */
class AndroidLogger(globalTag: String) : AppLogger {
    private val tag: String = globalTag

    private fun callerFileLine(): String {
        val stack = Exception("capture").stackTrace
        val loggerPkg = this::class.java.packageName
        for (el in stack) {
            val cn = el.className
            // Skip frames from logger package, logger facade, kotlin reflection or synthetic '$' classes
            val skip = cn.startsWith(loggerPkg) ||
                cn.contains("LoggerFacade") ||
                cn.contains("kotlin.reflect") ||
                cn.contains("$")
            if (!skip) {
                val file = el.fileName ?: cn.substringAfterLast('.')
                val line = el.lineNumber.takeIf { it >= 0 } ?: 0
                return "$file:$line"
            }
        }
        // Fallback: last non-null filename in stack
        val alt = stack.lastOrNull { it.fileName != null }
        return if (alt != null) {
            val line = alt.lineNumber.takeIf { it >= 0 } ?: 0
            "${alt.fileName}:$line"
        } else {
            "?"
        }
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
