/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.e2e

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

object TestLaunchUtils {
    private const val DEFAULT_WAIT_MS = 10_000L

    /**
     * Brings the app to the foreground using the launch intent and waits for
     * the app's package to appear as the active window. Also wakes the screen
     * and presses Home first to avoid OEM lock/overlay issues.
     */
    fun bringAppToForeground(appPackage: String, timeoutMs: Long = DEFAULT_WAIT_MS): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)

        // Wake the screen if needed (avoid pressing Home which can background/kill tasks on some OEMs)
        try { if (!device.isScreenOn) device.wakeUp() } catch (_: Throwable) {}

        val launchIntent = context.packageManager.getLaunchIntentForPackage(appPackage)
            ?: return false
        // Bring existing task to front without destroying it. Avoid CLEAR_TASK.
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        context.startActivity(launchIntent)

        // Helper: wait until the app is really foregrounded
        fun waitUntilForeground(timeout: Long): Boolean {
            val deadline = SystemClock.uptimeMillis() + timeout
            while (SystemClock.uptimeMillis() < deadline) {
                // Check current package and existence in view hierarchy
                if (device.currentPackageName == appPackage) return true
                if (device.findObject(By.pkg(appPackage)) != null) return true
                // Also try UiAutomator wait in small slices
                if (device.wait(Until.hasObject(By.pkg(appPackage)), 250)) return true
                SystemClock.sleep(150)
            }
            return false
        }

        if (waitUntilForeground(timeoutMs)) return true

        // As a last attempt, try starting again without REORDER_TO_FRONT (still no CLEAR_TASK)
        return try {
            val retryIntent = context.packageManager.getLaunchIntentForPackage(appPackage)
                ?: return false
            retryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(retryIntent)
            waitUntilForeground(timeoutMs / 2)
        } catch (_: Throwable) {
            false
        }
    }
}
