/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.e2e

import android.os.SystemClock
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.Matcher
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Step 4 helper: open Reader appearance sheet and verify settings mutations:
 * 1) Font size increases (A+ then A++)
 * 2) Line height increases (Relaxed then Loose)
 * 3) Background color changes (Olive then Sepia)
 */
class Step04_AdjustReaderAppearance {
    fun openAppearance_andVerifyReaderSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // Bring app to foreground and ensure we're on the Reader WebView
        val appPkg = instrumentation.targetContext.packageName
        TestLog.mark("bring app to foreground - Step04")
        TestLaunchUtils.bringAppToForeground(appPkg, timeoutMs = 5_000)
        TestLog.mark("waiting for WebView on Reader")
        var webVisible = device.wait(Until.hasObject(By.clazz(WebView::class.java)), 15_000)
        if (!webVisible) {
            // Attempt a light recovery to reach Reader: if on Articles list, open first card; if on Home, open first menu then first card
            TestLog.mark("Step04: WebView not visible; attempting recovery navigation to Reader")
            // Try from Articles list
            var card = device.findObject(By.desc("Article card"))
            if (card != null) {
                try { card.click() } catch (_: Throwable) {}
            } else {
                // If on Home, open first menu item
                val homeItem = device.findObject(By.desc("Home menu item")) ?: device.findObject(By.desc("Home menu title"))
                if (homeItem != null) {
                    try { homeItem.click() } catch (_: Throwable) {}
                    device.wait(Until.hasObject(By.desc("Article card")), 8_000)
                    card = device.findObject(By.desc("Article card"))
                    if (card != null) { try { card.click() } catch (_: Throwable) {} }
                } else {
                    // One back press to unwind any modal, then re-check for articles/home quickly
                    try { device.pressBack() } catch (_: Throwable) {}
                    device.waitForIdle(300)
                    card = device.findObject(By.desc("Article card"))
                    if (card != null) { try { card.click() } catch (_: Throwable) {}
                    } else {
                        val hi = device.findObject(By.desc("Home menu item")) ?: device.findObject(By.desc("Home menu title"))
                        if (hi != null) {
                            try { hi.click() } catch (_: Throwable) {}
                            device.wait(Until.hasObject(By.desc("Article card")), 6_000)
                            val c2 = device.findObject(By.desc("Article card"))
                            if (c2 != null) { try { c2.click() } catch (_: Throwable) {} }
                        }
                    }
                }
            }
            // Final wait for WebView
            webVisible = device.wait(Until.hasObject(By.clazz(WebView::class.java)), 10_000)
            if (!webVisible) {
                TestAbortState.markAborted()
                fail("Step04: Reader WebView not visible")
            }
        }

        // Ensure the Reader action rail toggle is clickable; reveal it if hidden; then wait for sheet
        TestLog.mark("ensure Reader actions sheet opens")
        ensureActionsSheetOpen(device)

        // Now click the "Appearance" item inside the actions bottom sheet (robustly)
        TestLog.mark("open Appearance item from actions sheet")
        waitForAnyTextOrDesc(device, listOf("Appearance", "Listen"), timeoutMs = 6_000)
        var clickedAppearance = clickByTextOrDesc(device, "Appearance", timeoutMs = 4_000)
        if (!clickedAppearance) {
            // Try a gentle heuristic tap around where first body item usually is
            clickedAppearance = tryClickAppearanceHeuristic(device)
        }
        if (!clickedAppearance) {
            // If still not clicked, re-open the sheet and try once more by text
            ensureActionsSheetOpen(device)
            clickedAppearance = clickByTextOrDesc(device, "Appearance", timeoutMs = 4_000) ||
                tryClickAppearanceHeuristic(device)
        }
        if (!clickedAppearance) {
            TestAbortState.markAborted()
            fail("Step04: Could not tap Appearance in actions sheet")
        }

        // Wait for appearance sheet content; accept any of the known labels as signal
        val sheetVisible = waitForAnyTextOrDesc(
            device,
            listOf(
                "Reader appearance",
                "Font size",
                "Line height",
                "Background",
                "A-",
                "A",
                "A+",
                "A++",
                "A+++",
                "Tight",
                "Normal",
                "Relaxed",
                "Loose",
                "Olive",
                "Sepia",
                "Paper",
                "Gray",
                "Slate",
                "Charcoal",
                "Night",
            ),
            10_000,
        )
        if (!sheetVisible) {
            // As a last fallback, try opening the rail and selecting Appearance once more
            TestLog.mark("appearance labels not found; retry opening")
            ensureActionsSheetOpen(device)
            if (clickByTextOrDesc(device, "Appearance", timeoutMs = 3_000)) {
                if (!waitForAnyTextOrDesc(device, listOf("Reader appearance", "Font size", "Line height"), 4_000)) {
                    TestAbortState.markAborted()
                    fail("Step04: Appearance sheet not visible after retry")
                }
            } else {
                TestAbortState.markAborted()
                fail("Step04: Appearance sheet not visible (labels not found)")
            }
        }

        // 1) Font size increases: A+ then A++ (poll after each selection until CSS increases)
        TestLog.mark("font size: A+ then A++")
        val fontBaseline = readWebViewNumericCss("fontSize", 5_000)
        require(clickByTextOrDesc(device, "A+", 5_000)) { "Step04: A+ not found" }
        device.waitForIdle(200)
        val fontAfterPlus = awaitCssNumericIncrease(prop = "fontSize", baseline = fontBaseline, timeoutMs = 6_000)
        require(clickByTextOrDesc(device, "A++", 5_000)) { "Step04: A++ not found" }
        device.waitForIdle(200)
        val fontAfterPlusPlus = awaitCssNumericIncrease(prop = "fontSize", baseline = fontAfterPlus, timeoutMs = 6_000)
        assertTrue(
            "Expected font size to increase: A+=$fontAfterPlus, A++=$fontAfterPlusPlus",
            fontAfterPlusPlus > fontAfterPlus,
        )

        // 2) Line height increases: Relaxed then Loose (poll for increase after each selection)
        TestLog.mark("line height: Relaxed then Loose")
        val lhBase = readWebViewNumericCss("lineHeight", 5_000)
        require(clickByTextOrDesc(device, "Relaxed", 5_000)) { "Step04: Relaxed not found" }
        device.waitForIdle(200)
        val lhRelaxed = awaitCssNumericIncrease(prop = "lineHeight", baseline = lhBase, timeoutMs = 6_000)
        require(clickByTextOrDesc(device, "Loose", 5_000)) { "Step04: Loose not found" }
        device.waitForIdle(200)
        val lhLoose = awaitCssNumericIncrease(prop = "lineHeight", baseline = lhRelaxed, timeoutMs = 6_000)
        assertTrue(
            "Expected line height to increase: Relaxed=$lhRelaxed, Loose=$lhLoose",
            lhLoose > lhRelaxed,
        )

        // 3) Background color changes: Olive then Sepia
        TestLog.mark("background: Olive then Sepia")
        val bgInitial = readWebViewColorCss("backgroundColor", 5_000)
        val bgOlive = clickBackgroundAndAwaitChange(device, label = "Olive", prev = bgInitial, totalTimeoutMs = 6_000)
        val bgSepia = clickBackgroundAndAwaitChange(device, label = "Sepia", prev = bgOlive, totalTimeoutMs = 6_000)
        assertTrue(
            "Expected background to change from Olive to Sepia: olive=$bgOlive, sepia=$bgSepia",
            !bgOlive.equals(bgSepia, ignoreCase = true),
        )

        // Dismiss the appearance sheet to ensure Reader WebView is visible for next step
        TestLog.mark("dismiss appearance sheet")
        dismissAppearanceIfVisible(device)
        // Guard: ensure we are still in Reader (WebView visible). If not, recover by reopening the first article.
        TestLog.mark("verify Reader still visible after Step04")
        var stillInReader = device.wait(Until.hasObject(By.clazz(WebView::class.java)), 4_000)
        if (!stillInReader) {
            // If we accidentally navigated out, reopen first article from Articles/Home
            val article = device.findObject(By.desc("Article card"))
            if (article != null) {
                try { article.click() } catch (_: Throwable) {}
            } else {
                val homeItem = device.findObject(By.desc("Home menu item")) ?: device.findObject(By.desc("Home menu title"))
                if (homeItem != null) {
                    try { homeItem.click() } catch (_: Throwable) {}
                    device.wait(Until.hasObject(By.desc("Article card")), 6_000)
                    val card = device.findObject(By.desc("Article card"))
                    if (card != null) { try { card.click() } catch (_: Throwable) {} }
                }
            }
            stillInReader = device.wait(Until.hasObject(By.clazz(WebView::class.java)), 10_000)
            if (!stillInReader) {
                TestAbortState.markAborted()
                fail("Step04: Reader WebView not visible after dismissal/recovery")
            }
        }
        TestLog.mark("step04 complete")
    }

    /** Clicks the background swatch identified by its label by attempting to tap just above the label text,
     *  then polls the WebView until the computed background color changes from [prev] or times out. */
    private fun clickBackgroundAndAwaitChange(
        device: UiDevice,
        label: String,
        prev: String,
        totalTimeoutMs: Long,
    ): String {
        val deadline = SystemClock.uptimeMillis() + totalTimeoutMs
        var last = prev
        while (SystemClock.uptimeMillis() < deadline) {
            // Try a precise click on the label (may work if parent is clickable)
            if (!clickByTextOrDesc(device, label, timeoutMs = 800)) {
                // Fall through to bounds-based tap
            }
            // Bounds-based tap just above the label to hit the circular swatch
            val labelObj = device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
            if (labelObj != null) {
                val b = labelObj.visibleBounds
                val cx = b.centerX()
                // Try a few offsets above the label to land on the swatch circle regardless of density
                val offsets = intArrayOf(16, 24, 32, 40, 48)
                var tapped = false
                for (off in offsets) {
                    val y = (b.top - off).coerceAtLeast(0)
                    try {
                        device.click(cx, y)
                        tapped = true
                        break
                    } catch (_: Throwable) {
                        // retry with next offset
                    }
                }
                if (!tapped) {
                    // As a last resort, click the center of the label (some layouts propagate)
                    try { device.click(cx, b.centerY()) } catch (_: Throwable) {}
                }
            }

            device.waitForIdle(200)
            val now = readWebViewColorCss("backgroundColor", 2_000)
            if (now.isNotBlank() && !now.equals(last, ignoreCase = true)) {
                return now
            }
        }
        // Timed out; return last seen value (will likely fail assertion upstack)
        return last
    }

    private fun clickByTextOrDesc(device: UiDevice, label: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val byText = device.findObject(By.text(label))
            if (byText != null) {
                try { byText.click(); return true } catch (_: Throwable) {}
            }
            val byDesc = device.findObject(By.desc(label))
            if (byDesc != null) {
                try { byDesc.click(); return true } catch (_: Throwable) {}
            }
            SystemClock.sleep(150)
        }
        return false
    }

    private fun waitForAnyTextOrDesc(device: UiDevice, labels: List<String>, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            for (l in labels) {
                if (device.findObject(By.text(l)) != null || device.findObject(By.desc(l)) != null) {
                    return true
                }
            }
            SystemClock.sleep(150)
        }
        return false
    }

    private fun clickBottomRightToggleHeuristic(device: UiDevice): Boolean {
        val w = device.displayWidth
        val h = device.displayHeight
        val xs = intArrayOf((w * 0.92f).toInt(), (w * 0.88f).toInt(), (w * 0.96f).toInt())
        val ys = intArrayOf((h * 0.88f).toInt(), (h * 0.92f).toInt(), (h * 0.84f).toInt())
        for (y in ys) {
            for (x in xs) {
                try {
                    device.click(x, y)
                    device.waitForIdle(250)
                    // If any sheet label is now visible, consider success
                    if (waitForAnyTextOrDesc(device, listOf("Appearance", "Settings", "Listen"), 600)) {
                        return true
                    }
                } catch (_: Throwable) {
                    // continue
                }
            }
        }
        return false
    }

    /**
     * Heuristic tap for Compose bottom sheet: try a few y-positions in the lower half of the screen
     * around center-x to hit the first body item ("Appearance"). Returns true once a tap is issued.
     */
    private fun tryClickAppearanceHeuristic(device: UiDevice): Boolean {
        val cx = device.displayWidth / 2
        val h = device.displayHeight
        val ys = intArrayOf(
            (h * 0.65f).toInt(),
            (h * 0.70f).toInt(),
            (h * 0.75f).toInt(),
            (h * 0.80f).toInt(),
        )
        for (y in ys) {
            try {
                device.click(cx, y)
                device.waitForIdle(200)
                // Consider it a tentative click; the next wait for labels will confirm
                return true
            } catch (_: Throwable) {
                // try next position
            }
        }
        return false
    }

    private fun ensureActionsSheetOpen(device: UiDevice) {
        val deadline = SystemClock.uptimeMillis() + 6_000
        while (SystemClock.uptimeMillis() < deadline) {
            // If already visible, return
            if (waitForAnyTextOrDesc(device, listOf("Appearance", "Listen"), timeoutMs = 350)) return

            // Try clicking the toggle by content-desc
            var opened = clickByTextOrDesc(device, "Reader actions", timeoutMs = 600)
            if (!opened) {
                // Reveal FAB by tapping center of WebView, then try again
                val cx = device.displayWidth / 2
                val cy = device.displayHeight / 2
                device.click(cx, cy)
                device.waitForIdle(200)
                opened = clickByTextOrDesc(device, "Reader actions", timeoutMs = 600)
            }
            if (!opened) {
                // Coordinate fallback near bottom-right where the toggle lives
                clickBottomRightToggleHeuristic(device)
            }
            // Give a moment for sheet animation and then re-check
            device.waitForIdle(350)
            if (waitForAnyTextOrDesc(device, listOf("Appearance", "Listen"), timeoutMs = 600)) return
        }
        // If we exited the loop, we failed to open the sheet
        TestAbortState.markAborted()
        fail("Step04: Could not open Reader actions sheet")
    }

    private fun isAppearanceSheetVisible(device: UiDevice): Boolean {
        return waitForAnyTextOrDesc(
            device,
            listOf(
                "Reader appearance",
                "Font size",
                "Line height",
                "Background",
            ),
            timeoutMs = 350,
        )
    }

    private fun dismissAppearanceIfVisible(device: UiDevice) {
        // If appearance labels are visible, try tapping scrim (top area) or swipe down on the sheet; avoid back press
        val deadline = SystemClock.uptimeMillis() + 6_000
        while (SystemClock.uptimeMillis() < deadline) {
            val visible = isAppearanceSheetVisible(device)
            if (!visible) return
            // 1) Tap scrim near top-center to dismiss
            val cx = device.displayWidth / 2
            val yTop = (device.displayHeight * 0.15f).toInt().coerceAtLeast(10)
            runCatching { device.click(cx, yTop) }
            device.waitForIdle(250)
            if (!isAppearanceSheetVisible(device)) return
            // 2) Swipe down gesture from middle to bottom to collapse bottom sheet
            val startY = (device.displayHeight * 0.70f).toInt()
            val endY = (device.displayHeight * 0.95f).toInt()
            runCatching { device.swipe(cx, startY, cx, endY, /*steps*/ 24) }
            device.waitForIdle(300)
            if (!isAppearanceSheetVisible(device)) return
            // 3) Toggle the actions button again to close sheet if it acts as a toggle
            if (clickByTextOrDesc(device, "Reader actions", timeoutMs = 600)) {
                device.waitForIdle(250)
                if (!isAppearanceSheetVisible(device)) return
            }
            // Loop and retry until timeout
        }
    }

    private fun readWebViewNumericCss(prop: String, timeoutMs: Long): Double {
        val ref = java.util.concurrent.atomic.AtomicReference<Double?>(null)
        onView(isAssignableFrom(WebView::class.java))
            .perform(ReadCssNumericAction(prop, ref, timeoutMs))
        return ref.get() ?: 0.0
    }

    private fun readWebViewColorCss(prop: String, timeoutMs: Long): String {
        val ref = java.util.concurrent.atomic.AtomicReference<String?>(null)
        onView(isAssignableFrom(WebView::class.java))
            .perform(ReadCssColorAction(prop, ref, timeoutMs))
        return ref.get() ?: ""
    }

    private fun awaitCssNumericIncrease(prop: String, baseline: Double, timeoutMs: Long): Double {
        val start = SystemClock.uptimeMillis()
        var last = baseline
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val now = readWebViewNumericCss(prop, 2_000)
            if (now > baseline) return now
            last = now
            SystemClock.sleep(120)
        }
        return last
    }

    private class ReadCssNumericAction(
        private val prop: String,
        private val out: java.util.concurrent.atomic.AtomicReference<Double?>,
        private val timeoutMs: Long,
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "Read numeric CSS $prop from WebView (timeout ${timeoutMs}ms)"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            val start = SystemClock.uptimeMillis()
            var last: Double? = null
            while (SystemClock.uptimeMillis() - start < timeoutMs) {
                val resultRef = java.util.concurrent.atomic.AtomicReference<String?>()
                val js = (
                    "(function(){try{" +
                        // Prefer a representative text node (paragraph) for accurate typography reads
                        "var target = document.querySelector('main.article p, article p, .article p, body p');" +
                        "var root = target || document.querySelector('main.article') || document.body;" +
                        // Access computed style property by name using bracket notation
                        "var v = window.getComputedStyle(root)['$prop'];" +
                        // Extract number (px or unitless)
                        "var num = 0;" +
                        "if(typeof v==='string'){var m=v.match(/([0-9]+\\.?[0-9]*)/); if(m){ num=parseFloat(m[1]); }}" +
                        "return ''+num;" +
                    "}catch(e){return '0';}})()"
                )
                try { web.evaluateJavascript(js, ValueCallback { v -> resultRef.set(v) }) } catch (_: Throwable) { resultRef.set("0") }
                val rs = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - rs < 800) {
                    val got = resultRef.get()
                    if (got != null) break
                    uiController.loopMainThreadForAtLeast(50)
                }
                val raw = (resultRef.get() ?: "\"0\"").trim('"')
                val num = raw.toDoubleOrNull()
                if (num != null) {
                    last = num
                    out.set(num)
                    return
                }
                uiController.loopMainThreadForAtLeast(200)
            }
            out.set(last)
        }
    }

    private class ReadCssColorAction(
        private val prop: String,
        private val out: java.util.concurrent.atomic.AtomicReference<String?>,
        private val timeoutMs: Long,
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "Read color CSS $prop from WebView (timeout ${timeoutMs}ms)"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            val start = SystemClock.uptimeMillis()
            var last: String? = null
            while (SystemClock.uptimeMillis() - start < timeoutMs) {
                val resultRef = java.util.concurrent.atomic.AtomicReference<String?>()
                val js = (
                    "(function(){try{" +
                        // Find a representative node, then walk ancestors to find effective non-transparent background
                        "var target = document.querySelector('main.article p, article p, .article p, body p') || document.querySelector('main.article') || document.body;" +
                        "function isTransparent(c){ if(!c) return true; c = (''+c).toLowerCase(); if(c==='transparent') return true; var m=c.match(/rgba?\\(([^)]+)\\)/); if(!m) return false; var parts=m[1].split(',').map(function(x){return x.trim();}); if(parts.length===4){ var a=parseFloat(parts[3]); return isNaN(a) ? false : a===0; } return false; }" +
                        "function effectiveBg(el){ var last=''; while(el){ var v = window.getComputedStyle(el)['$prop']; if(v && !isTransparent(v)) return v; last=v||last; el = el.parentElement; } return last || 'rgba(0,0,0,0)'; }" +
                        "var v = effectiveBg(target);" +
                        "return ''+v;" +
                    "}catch(e){return '';}})()"
                )
                try { web.evaluateJavascript(js, ValueCallback { v -> resultRef.set(v) }) } catch (_: Throwable) { resultRef.set("") }
                val rs = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - rs < 800) {
                    val got = resultRef.get()
                    if (got != null) break
                    uiController.loopMainThreadForAtLeast(50)
                }
                val raw = (resultRef.get() ?: "\"\"").trim('"')
                if (raw.isNotBlank()) {
                    last = raw
                    out.set(raw)
                    return
                }
                uiController.loopMainThreadForAtLeast(200)
            }
            out.set(last ?: "")
        }
    }
}
