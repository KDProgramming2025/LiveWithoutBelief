/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import info.lwb.e2e.TestLaunchUtils.bringAppToForeground
import org.junit.Assert.fail

class Step02_ArticlesListTest {
    fun openFirstMenuAndAssertArticles() {
        // Preconditions: Step01 validated Home menu. Use the same app process.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        // Ensure the app/activity is launched; this is a no-op if already running
    TestLog.mark("bring app to foreground - Step02")

        // Bring the app task to the foreground explicitly (handles cases where OS/backgrounded between classes)
        val appPkg = instrumentation.targetContext.packageName
        bringAppToForeground(appPkg, timeoutMs = 5_000L)

        // Wait up to 10s for Home menu items to be present; if not visible, recover like Step03
        val homeWaitMs = 10_000L
        TestLog.mark("waiting for Home menu items")
        var homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), homeWaitMs)
        if (!homeVisible) {
            TestLog.mark("home not visible - attempting back/foreground recovery loop")
            val appPkg = instrumentation.targetContext.packageName
            var attempts = 0
            while (!homeVisible && attempts < 5) {
                device.pressBack()
                Thread.sleep(250)
                if (device.currentPackageName != appPkg) {
                    bringAppToForeground(appPkg, timeoutMs = 5_000L)
                }
                homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), 2_000)
                attempts++
            }
            if (!homeVisible) {
                bringAppToForeground(appPkg, timeoutMs = 5_000L)
                homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), 10_000)
            }
        }

        // Tap the first Home menu item with retry to avoid StaleObjectException
        val haveAny = device.wait(Until.hasObject(By.desc("Home menu item")), 10_000)
        if (!haveAny) {
            TestAbortState.markAborted()
            fail("No Home menu items found to tap in Step02")
        }
        TestLog.mark("tapping first Home menu item (robust)")
        val tapDeadline = System.currentTimeMillis() + 5_000
        var tapped = false
        while (!tapped && System.currentTimeMillis() < tapDeadline) {
            val first = device.findObject(By.desc("Home menu item"))
            if (first != null) {
                try {
                    // Prefer direct click; fall back to bounds-based tap if stale
                    first.click()
                    tapped = true
                } catch (t: Throwable) {
                    // Retry with bounds click in case of stale object
                    try {
                        val b = first.visibleBounds
                        device.click(b.centerX(), b.centerY())
                        tapped = true
                    } catch (_: Throwable) {
                        // Re-fetch on next loop
                        device.waitForIdle(100)
                    }
                }
            } else {
                device.wait(Until.hasObject(By.desc("Home menu item")), 500)
            }
        }
        if (!tapped) {
            TestAbortState.markAborted()
            fail("Failed to tap Home menu item due to stale UI object")
        }

        // Wait for at least one article card to appear in the Articles list screen
        val timeoutMs = 10_000L
        TestLog.mark("waiting for articles list")
        val appeared = device.wait(Until.hasObject(By.desc("Article card")), timeoutMs)
        val articles = device.findObjects(By.desc("Article card"))
        val count = articles.size
        if (!appeared || count < 1) {
            TestAbortState.markAborted()
            fail("Expected at least 1 article card within ${timeoutMs} ms after tapping a menu item, but found $count")
        }

        // Per-article assertions: exactly one icon (image or placeholder), one cover (image or placeholder), one title
        val iconCount = device.findObjects(By.desc("Article icon - image")).size +
            device.findObjects(By.desc("Article icon - placeholder")).size
        val coverCount = device.findObjects(By.desc("Article cover - image")).size +
            device.findObjects(By.desc("Article cover - placeholder")).size
        val titleCount = device.findObjects(By.desc("Article title")).size

        TestLog.mark("asserting per-article counts: count=$count, icons=$iconCount, covers=$coverCount, titles=$titleCount")
        if (iconCount != count || coverCount != count || titleCount != count) {
            TestAbortState.markAborted()
            fail("Expected exactly one icon, one cover, and one title per article card, but found icons=${iconCount}, covers=${coverCount}, titles=${titleCount}, articles=${count}")
        }
        TestLog.mark("step02 complete")
    }
}
