/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.e2e

import android.util.Log

/**
 * Simple test-side logger to emit precise markers into logcat with file:line info.
 * These markers allow correlating Activity lifecycle logs with exact test code locations.
 */
object TestLog {
    private const val TAG = "E2E"

    /** Logs a marker with the caller's file and line number. */
    fun mark(message: String = "") {
        val (file, line) = callerFileLine()
        val msg = if (message.isNotEmpty()) message else ""
        Log.i(TAG, "[MARK][$file:$line] $msg")
    }

    private fun callerFileLine(): Pair<String, Int> {
        val stack = Throwable().stackTrace
        // Prefer first frame in our androidTest package that is not this object
        for (el in stack) {
            val cls = el.className
            if (cls.startsWith("info.lwb.e2e") && !cls.contains("TestLog")) {
                return Pair(el.fileName ?: cls.substringAfterLast('.'), el.lineNumber)
            }
        }
        // Fallback to the first non-internal frame
        val first = stack.firstOrNull { !it.className.contains("TestLog") }
        return Pair(first?.fileName ?: "?", first?.lineNumber ?: -1)
    }
}
