/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.e2e

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Clears logcat and adds START/END markers for a test method, to make it easy to grab
 * the relevant log slice and correlate app lifecycle logs with the test timeline.
 */
class LogcatCaptureRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Clear buffer to keep logs minimal to this test
                try {
                    Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                } catch (_: Throwable) {
                    // ignore on environments where logcat isn't available
                }
                val id = description.className.substringAfterLast('.') + "#" + description.methodName
                Log.i("E2E", "[TEST-START] $id")
                try {
                    base.evaluate()
                } finally {
                    Log.i("E2E", "[TEST-END] $id")
                }
            }
        }
    }
}
