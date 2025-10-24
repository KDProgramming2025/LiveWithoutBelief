/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.e2e

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.fail

/**
 * Step 1 helper: launch and validate Home menu basics.
 */
class Step01_HomeMenuTest {
    fun homeMenu_showsAtLeastOneItem_withIconAndTitle() {
        // Minimal: launch, wait up to 10s, assert at least one "Home menu item" is present
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
    TestLog.mark("bring app to foreground - Step01")
    val appPkg = instrumentation.targetContext.packageName
    TestLaunchUtils.bringAppToForeground(appPkg, timeoutMs = 10_000)
    TestLog.mark("foregrounded - ensuring Home screen then waiting for Home menu items")
        // Let the window/Compose settle and ensure accessibility root becomes available
        runCatching {
            device.wait(Until.hasObject(By.pkg(appPkg)), /*timeout*/ 3_000)
            device.waitForIdle(1_000)
        }
        // One-time fallback: if accessibility root is flaky right after resume, briefly go Home and re-foreground
        runCatching {
            // Probe once; if we can't retrieve any node, toggle foreground once
            val probe = device.findObject(By.pkg(appPkg))
            if (probe == null) {
                TestLog.mark("Step01: accessibility root not ready, toggling foreground once")
                device.pressHome()
                SystemClock.sleep(300)
                TestLaunchUtils.bringAppToForeground(appPkg, timeoutMs = 5_000)
                device.wait(Until.hasObject(By.pkg(appPkg)), 2_000)
                device.waitForIdle(800)
            }
        }
        // Immediately attempt to unwind to Home in case app restored into Reader or Articles
        recoverToHome(device)
        val timeoutMs = 10_000L
        // Poll until at least one Home menu item appears; around 4s mark try a refresh (Retry or pull-to-refresh)
        var count = 0
        val waitDeadline = System.currentTimeMillis() + timeoutMs
        var didRefresh = false
        var didEarlyRefresh = false

        // Proactively trigger a refresh shortly after foreground to kick network fetchers
        SystemClock.sleep(300)
        runCatching {
            val cx = device.displayWidth / 2
            val startY = (device.displayHeight * 0.20f).toInt()
            val endY = (device.displayHeight * 0.60f).toInt()
            device.swipe(cx, startY, cx, endY, /*steps*/ 16)
            didEarlyRefresh = true
            TestLog.mark("Step01: performed early pull-to-refresh")
        }
        while (System.currentTimeMillis() < waitDeadline) {
            val itemsCount = device.findObjects(By.desc("Home menu item")).size
            val titlesCount = device.findObjects(By.desc("Home menu title")).size
            count = maxOf(itemsCount, titlesCount)
            if (count >= 1) break
            // If still nothing after ~4s, try a UI-level refresh once
            if (!didRefresh && (waitDeadline - System.currentTimeMillis()) <= (timeoutMs - 4_000)) {
                didRefresh = true
                // Prefer tapping explicit Retry if visible
                val retry = device.findObject(By.text("Retry")) ?: device.findObject(By.desc("Retry"))
                if (retry != null) {
                    try { retry.click() } catch (_: Throwable) {}
                } else {
                    // Pull-to-refresh gesture: swipe from top-middle downwards
                    val cx = device.displayWidth / 2
                    val startY = (device.displayHeight * 0.20f).toInt()
                    val endY = (device.displayHeight * 0.60f).toInt()
                    try { device.swipe(cx, startY, cx, endY, /*steps*/ 20) } catch (_: Throwable) {}
                }
            } else if (!didEarlyRefresh && (waitDeadline - System.currentTimeMillis()) <= (timeoutMs - 2_000)) {
                // As an extra nudge around ~2s elapsed if early refresh failed
                val cx = device.displayWidth / 2
                val startY = (device.displayHeight * 0.20f).toInt()
                val endY = (device.displayHeight * 0.60f).toInt()
                runCatching { device.swipe(cx, startY, cx, endY, /*steps*/ 16) }
                didEarlyRefresh = true
            }
            Thread.sleep(120)
        }
        if (count < 1) {
            TestLog.mark("Home items not visible after initial wait; attempting back-stack recovery")
            recoverToHome(device)
            // Final quick re-check (up to 2s) after recovery
            val againDeadline = System.currentTimeMillis() + 2_000
            while (System.currentTimeMillis() < againDeadline && count < 1) {
                val itemsCount = device.findObjects(By.desc("Home menu item")).size
                val titlesCount = device.findObjects(By.desc("Home menu title")).size
                count = maxOf(itemsCount, titlesCount)
                Thread.sleep(100)
            }
        }
        if (count < 1) {
            TestAbortState.markAborted()
            fail("Expected at least 1 home menu item within ${timeoutMs} ms, but found $count")
        }
        TestLog.mark("home menu items visible: count=$count - asserting icon/title counts")
        // Assert each item shows exactly one icon and one title visible, with a short stabilization window
        var iconsCount: Int
        var titlesCount: Int
        val stabilizeDeadline = System.currentTimeMillis() + 3_000
        do {
            iconsCount = device.findObjects(By.desc("Home menu icon - image")).size +
                device.findObjects(By.desc("Home menu icon - placeholder")).size
            titlesCount = device.findObjects(By.desc("Home menu title")).size
            if (iconsCount == count && titlesCount == count) break
            Thread.sleep(100)
        } while (System.currentTimeMillis() < stabilizeDeadline)
        if (iconsCount != count || titlesCount != count) {
            TestAbortState.markAborted()
            fail("Expected exactly one icon and one title per menu item, but found icons=${iconsCount}, titles=${titlesCount}, items=${count}")
        }
        TestLog.mark("step01 complete")
        // Success: proceed to next steps
    }

    private fun recoverToHome(device: UiDevice) {
        // If on Reader (WebView), press back and confirm exit if dialog appears
        val isReader = device.findObject(By.clazz(android.webkit.WebView::class.java)) != null
        if (isReader) {
            try { device.pressBack() } catch (_: Throwable) {}
            device.waitForIdle(200)
            // Look for the confirm-exit dialog and tap Exit
            val exitBtn = device.findObject(By.text("Exit")) ?: device.findObject(By.desc("Exit"))
            if (exitBtn != null) {
                try { exitBtn.click() } catch (_: Throwable) {}
                device.waitForIdle(400)
            } else {
                // Press back again as a fallback
                try { device.pressBack() } catch (_: Throwable) {}
                device.waitForIdle(300)
            }
        }

        // If on Articles list, one more back should land on Home
        val onArticles = device.findObject(By.desc("Article card")) != null
        if (onArticles) {
            try { device.pressBack() } catch (_: Throwable) {}
            device.waitForIdle(300)
        }
    }
}
