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
 * Step 5: Persisted appearance + scroll restoration.
 * - Capture current font size, line height, background color, and scrollY from the WebView.
 * - Animate scroll to a random position over ~2 seconds.
 * - Navigate back to the Articles list, then open the first article again.
 * - After page load, wait at least 5 seconds for settings/scroll restoration, then verify all values restored.
 */
class Step05_VerifyStateRestoration {
    fun animateScroll_exitAndReopen_assertRestored() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)

        // Ensure WebView is present (we are in reader from previous step)
        TestLog.mark("Step05: ensure WebView visible")
        val webVisible = device.wait(Until.hasObject(By.clazz(WebView::class.java)), 10_000)
        if (!webVisible) {
            TestAbortState.markAborted(); fail("Step05: Reader WebView not visible")
        }

        // Capture current settings and scroll from the WebView
        val font0 = readCssNumeric("fontSize", 10_000)
        val line0 = readCssNumeric("lineHeight", 10_000)
        val bg0 = readCssColor("backgroundColor", 10_000)
        val scroll0 = readScrollY(10_000)
        TestLog.mark("Step05: captured (font=$font0, line=$line0, bg=$bg0, scroll=$scroll0)")

        // Animate scroll to a random position over 2 seconds (2000ms)
        val target = computeRandomTarget(scroll0)
        TestLog.mark("Step05: animating scroll to target=$target")
        onView(isAssignableFrom(WebView::class.java)).perform(AnimateScrollAction(targetY = target, durationMs = 2_000))
    val scroll1 = readScrollY(5_000)
    TestLog.mark("Step05: after animate scrollY=" + scroll1)
    // Wait for scroll to be stable to ensure debounced save triggers
    waitForStableScroll(stableForMs = 600, timeoutMs = 2_000)
    // Nudge scroll to trigger any debounced observers and ensure a final change event
    onView(isAssignableFrom(WebView::class.java)).perform(NudgeScrollAction())
    waitForStableScroll(stableForMs = 400, timeoutMs = 1_500)
    // Allow a brief moment for persistence/debounced saves before leaving the page
    SystemClock.sleep(2_000)
    // Capture first visible paragraph signature to compare after reopen
    val paraBefore = readFirstVisibleParagraphSignature(3_000)
    TestLog.mark("Step05: visible para before exit: ${'$'}{paraBefore.summary()}")

        // Go back to Articles list and reopen first article
        TestLog.mark("Step05: navigate back to list")
        device.pressBack()
        device.waitForIdle(300)
        // If the confirm-exit dialog is shown, confirm exit
        handleConfirmExitIfShown(device, timeoutMs = 5_000)
        var listVisible = device.wait(Until.hasObject(By.desc("Article card")), 12_000)
        if (!listVisible) {
            // Fallback: press back once more in case navigation stack requires it
            device.pressBack()
            device.waitForIdle(300)
            handleConfirmExitIfShown(device, timeoutMs = 3_000)
            listVisible = device.wait(Until.hasObject(By.desc("Article card")), 8_000)
        }
        if (!listVisible) {
            TestAbortState.markAborted(); fail("Step05: Articles list not visible after back")
        }
        val firstCard = device.findObject(By.desc("Article card"))
        if (firstCard == null) { TestAbortState.markAborted(); fail("Step05: No article card to reopen") }
        TestLog.mark("Step05: reopening first article")
        firstCard.click()

        // Wait for WebView and allow restoration time (>= 5 seconds from problem statement)
        device.wait(Until.hasObject(By.clazz(WebView::class.java)), 15_000)
        // Wait for layout readiness
        onView(isAssignableFrom(WebView::class.java)).perform(WaitContentHeight(minHeight = 1, timeoutMs = 15_000))
        // Extra restoration grace period and polling for async settings reapply
    val restoreStart = SystemClock.uptimeMillis()
    val restoreMax = 20_000L
        SystemClock.sleep(1_200) // small initial settle

        // Read back values (with polling for font/line restoration)
        var fontR = readCssNumeric("fontSize", 5_000)
        var lineR = readCssNumeric("lineHeight", 5_000)
        val bgR = readCssColor("backgroundColor", 5_000)
        var scrollR = readScrollY(5_000)
        // Poll until font/line are restored or timeout
        while (SystemClock.uptimeMillis() - restoreStart < restoreMax &&
            !(nearlyEqual(font0, fontR) && nearlyEqual(line0, lineR))) {
            SystemClock.sleep(250)
            fontR = readCssNumeric("fontSize", 1_000)
            lineR = readCssNumeric("lineHeight", 1_000)
            // scroll can drift due to late layout; refresh snapshot periodically for logs/ratio check later
            scrollR = readScrollY(800)
        }
    TestLog.mark("Step05: restored (font=" + fontR + ", line=" + lineR + ", bg=" + bgR + ", scroll=" + scrollR + ")")
    // Wait for scroll to stabilize after any late image/layout shifts
    waitForStableScroll(stableForMs = 800, timeoutMs = 3_000)
    val paraAfter = readFirstVisibleParagraphSignature(3_000)
    TestLog.mark("Step05: visible para after reopen: " + paraAfter.summary())

        // Compute normalized scroll ratios to compare restoration independent of content height changes
        val hBefore = readContentHeight(5_000)
        val vhBefore = readClientHeight(5_000)
        val maxScrollBefore = kotlin.math.max(1, hBefore - vhBefore)
        val ratioTarget = target.toDouble() / maxScrollBefore.toDouble()

        val hAfter = readContentHeight(5_000)
        val vhAfter = readClientHeight(5_000)
        val maxScrollAfter = kotlin.math.max(1, hAfter - vhAfter)
        val ratioRestored = scrollR.toDouble() / maxScrollAfter.toDouble()
        var ratioNow = ratioRestored
        var paraNow = paraAfter
        val ratioTol = 0.12 // allow ~12% variance
        TestLog.mark("Step05: ratios (target=" + ratioTarget + ", restored=" + ratioNow + ", tol=" + ratioTol + ") heights(before=" + hBefore + "/" + vhBefore + " after=" + hAfter + "/" + vhAfter + ")")

        // Poll for a few seconds in case restoration applies asynchronously
        val pollDeadline = SystemClock.uptimeMillis() + 6_000
        var ratioOk = kotlin.math.abs(ratioNow - ratioTarget) <= ratioTol
        var paraOk: Boolean
        while (!ratioOk && SystemClock.uptimeMillis() < pollDeadline) {
            SystemClock.sleep(200)
            val sr = readScrollY(800)
            ratioNow = sr.toDouble() / maxScrollAfter.toDouble()
            paraNow = readFirstVisibleParagraphSignature(1_000)
            ratioOk = kotlin.math.abs(ratioNow - ratioTarget) <= ratioTol
            if (ratioOk) break
        }
        paraOk = paraBefore.matches(paraNow)

        // Assert restoration: exact match for font/line/background; scroll ratio within tolerance
        val scrollTolerance = 32 // legacy px tolerance (kept for logs)
        assertTrue("Step05: font not restored: expected=$font0 actual=$fontR", nearlyEqual(font0, fontR))
        assertTrue("Step05: line-height not restored: expected=$line0 actual=$lineR", nearlyEqual(line0, lineR))
        assertTrue(
            "Step05: background not restored: expected=$bg0 actual=$bgR",
            bg0.equals(bgR, ignoreCase = true),
        )
        assertTrue(
            "Step05: scroll restore mismatch. ratioOk=" + ratioOk + " paraOk=" + paraOk + " targetRatio=" + ratioTarget + " actualRatio=" + ratioNow + " targetPx=" + target + " actualPx=" + scrollR + " beforePara=" + paraBefore.summary() + " afterPara=" + paraNow.summary(),
            ratioOk || paraOk,
        )
        TestLog.mark("step05 complete")
    }

    private fun nearlyEqual(a: Double, b: Double, eps: Double = 0.5): Boolean = kotlin.math.abs(a - b) <= eps

    private fun computeRandomTarget(current: Int): Int {
        // Use a deterministic pseudo-random target for stability: cycle between 25%, 50%, 75% of contentHeight
        val h = readContentHeight(5_000)
        val opts = listOf((h * 0.25).toInt(), (h * 0.5).toInt(), (h * 0.75).toInt())
        // pick the farthest from current among options to ensure visible movement
        return opts.maxByOrNull { kotlin.math.abs(it - current) } ?: (current + 500)
    }

    // Helpers to interact with WebView
    private fun readCssNumeric(prop: String, timeoutMs: Long): Double {
        val ref = java.util.concurrent.atomic.AtomicReference<Double?>(null)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read numeric CSS $prop"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: Double? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = (
                        "(function(){try{" +
                            "var target=document.querySelector('main.article p, article p, .article p, body p')||document.body;" +
                            "var v=window.getComputedStyle(target)['$prop'];" +
                            "var m=(typeof v==='string')?v.match(/([0-9]+\\.?[0-9]*)/):null;" +
                            "return m?m[1]:'0';" +
                        "}catch(e){return '0';}})()"
                    )
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("0") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 800) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "\"0\"").trim('"')
                    val num = raw.toDoubleOrNull()
                    if (num != null) { last = num; ref.set(num); return }
                    uiController.loopMainThreadForAtLeast(150)
                }
                ref.set(last ?: 0.0)
            }
        })
        return ref.get() ?: 0.0
    }

    private fun readCssColor(prop: String, timeoutMs: Long): String {
        val ref = java.util.concurrent.atomic.AtomicReference<String?>(null)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read color CSS $prop (effective non-transparent)"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: String? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = (
                        "(function(){try{" +
                            "var target=document.querySelector('main.article p, article p, .article p, body p')||document.body;" +
                            "function isTransparent(c){ if(!c) return true; c=(''+c).toLowerCase(); if(c==='transparent') return true; var m=c.match(/rgba?\\(([^)]+)\\)/); if(!m) return false; var parts=m[1].split(',').map(function(x){return x.trim();}); if(parts.length===4){ var a=parseFloat(parts[3]); return isNaN(a)?false:(a===0); } return false;}" +
                            "function eff(el){ var last=''; while(el){ var v=getComputedStyle(el)['$prop']; if(v && !isTransparent(v)) return v; last=v||last; el=el.parentElement; } return last||'rgba(0,0,0,0)'; }" +
                            "return ''+eff(target);" +
                        "}catch(e){return '';}})()"
                    )
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 800) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "\"\"").trim('"')
                    if (raw.isNotBlank()) { last = raw; ref.set(raw); return }
                    uiController.loopMainThreadForAtLeast(150)
                }
                ref.set(last ?: "")
            }
        })
        return ref.get() ?: ""
    }

    private fun readScrollY(timeoutMs: Long): Int {
        val ref = java.util.concurrent.atomic.AtomicReference<Int?>(null)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read window.scrollY"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: Int? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = "(function(){try{return String(Math.round(window.scrollY||0));}catch(e){return '0';}})()"
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("0") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 400) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "\"0\"").trim('"')
                    val num = raw.toIntOrNull()
                    if (num != null) { last = num; ref.set(num); return }
                    uiController.loopMainThreadForAtLeast(100)
                }
                ref.set(last ?: 0)
            }
        })
        return ref.get() ?: 0
    }

    private fun readClientHeight(timeoutMs: Long): Int {
        val ref = java.util.concurrent.atomic.AtomicReference<Int?>(null)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read viewport (client) height"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: Int? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = "(function(){try{var v=(document.documentElement&&document.documentElement.clientHeight)||window.innerHeight||0; return String(Math.round(v));}catch(e){return '0';}})()"
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("0") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 400) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "\"0\"").trim('"')
                    val num = raw.toIntOrNull()
                    if (num != null) { last = num; ref.set(num); return }
                    uiController.loopMainThreadForAtLeast(100)
                }
                ref.set(last ?: 0)
            }
        })
        return ref.get() ?: 0
    }

    private fun readContentHeight(timeoutMs: Long): Int {
        val ref = java.util.concurrent.atomic.AtomicReference<Int?>(null)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read document.body.scrollHeight"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: Int? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = "(function(){try{var b=document.scrollingElement||document.documentElement||document.body; return String(Math.round((b&&b.scrollHeight)||0));}catch(e){return '0';}})()"
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("0") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 400) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "\"0\"").trim('"')
                    val num = raw.toIntOrNull()
                    if (num != null) { last = num; ref.set(num); return }
                    uiController.loopMainThreadForAtLeast(100)
                }
                ref.set(last ?: 0)
            }
        })
        return ref.get() ?: 0
    }

    private fun readFirstVisibleParagraphSignature(timeoutMs: Long): ParaSig {
        val ref = java.util.concurrent.atomic.AtomicReference<ParaSig?>()
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Read first visible paragraph signature"
            override fun perform(uiController: UiController, view: View) {
                val web = view as WebView
                val start = SystemClock.uptimeMillis()
                var last: ParaSig? = null
                while (SystemClock.uptimeMillis() - start < timeoutMs) {
                    val r = java.util.concurrent.atomic.AtomicReference<String?>()
                    val js = (
                        "(function(){try{" +
                            "var paras=Array.prototype.slice.call(document.querySelectorAll('main.article p, article p, .article p, body p'));" +
                            "var vy=window.scrollY||0; var vh=(document.documentElement&&document.documentElement.clientHeight)||window.innerHeight||0;" +
                            "function topY(el){ var r=el.getBoundingClientRect(); return r.top; }" +
                            "var idx=-1, id='', text='', top=1e9;" +
                            "for(var i=0;i<paras.length;i++){ var t=topY(paras[i]); if(t>=-20 && t<top){ top=t; idx=i; id=paras[i].id||''; text=paras[i].textContent||''; } }" +
                            "if(idx<0 && paras.length){ idx=0; var r=paras[0].getBoundingClientRect(); top=r.top; id=paras[0].id||''; text=paras[0].textContent||''; }" +
                            "function clip(s){ s=(''+s).replace(/\\s+/g,' ').trim(); return s.length>80?s.slice(0,80):s; }" +
                            "return JSON.stringify({idx:idx,id:id,txt:clip(text)});" +
                        "}catch(e){return '{}';}})()"
                    )
                    try { web.evaluateJavascript(js, ValueCallback { s -> r.set(s) }) } catch (_: Throwable) { r.set("{}") }
                    val t0 = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - t0 < 400) { val g=r.get(); if (g!=null) break; uiController.loopMainThreadForAtLeast(50) }
                    val raw = (r.get() ?: "{}")
                    val sig = try { ParaSig.fromJson(raw) } catch (_: Throwable) { null }
                    if (sig != null) { last = sig; ref.set(sig); return }
                    uiController.loopMainThreadForAtLeast(100)
                }
                ref.set(last ?: ParaSig(-1, "", ""))
            }
        })
        return ref.get() ?: ParaSig(-1, "", "")
    }

    private fun waitForStableScroll(stableForMs: Long, timeoutMs: Long) {
        val start = SystemClock.uptimeMillis()
        var last = -1
        var stableStart = -1L
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            val y = readScrollY(500)
            if (y == last) {
                if (stableStart < 0) stableStart = SystemClock.uptimeMillis()
                if (SystemClock.uptimeMillis() - stableStart >= stableForMs) return
            } else {
                last = y
                stableStart = -1
            }
        }
    }

    private data class ParaSig(val idx: Int, val id: String, val txt: String) {
        fun matches(other: ParaSig): Boolean {
            if (this.idx >= 0 && other.idx >= 0 && kotlin.math.abs(this.idx - other.idx) <= 1) return true
            if (this.id.isNotBlank() && this.id == other.id) return true
            if (this.txt.isNotBlank() && other.txt.isNotBlank()) {
                val a = this.txt.trim()
                val b = other.txt.trim()
                if (a.equals(b, ignoreCase = true)) return true
                // fuzzy contains check
                if (a.length >= 16 && (b.contains(a, true) || a.contains(b, true))) return true
            }
            return false
        }
        fun summary(): String = "idx=${'$'}idx id='${'$'}id' txt='${'$'}txt'"
        companion object {
            fun fromJson(raw: String): ParaSig {
                val s = raw.trim()
                // naive parse to avoid bringing JSON libs
                fun extract(key: String): String {
                    val re = Regex(""""${'$'}key"\s*:\s*("([^\"]*)"|(-?\d+))""")
                    val m = re.find(s) ?: return ""
                    val g2 = m.groups[2]?.value
                    return g2 ?: m.groups[1]?.value?.trim('"') ?: ""
                }
                val idxStr = extract("idx")
                val id = extract("id")
                val txt = extract("txt")
                val idx = idxStr.toIntOrNull() ?: -1
                return ParaSig(idx, id, txt)
            }
        }
    }

    private class AnimateScrollAction(private val targetY: Int, private val durationMs: Long) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "Animate window scroll to $targetY over ${durationMs}ms"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            val js = (
                "(function(){try{" +
                    "var start = window.scrollY||0;" +
                    "var end = ${targetY};" +
                    "var dur = ${durationMs};" +
                    "var t0 = performance.now();" +
                    "function step(now){" +
                        "var e=Math.min(1,(now - t0)/dur);" +
                        "var y=Math.round(start + (end-start)*(e*e*(3-2*e)));" + // smoothstep easing
                        "window.scrollTo(0,y);" +
                        "if(e<1){ requestAnimationFrame(step); }" +
                    "}" +
                    "requestAnimationFrame(step); return 'ok';" +
                "}catch(e){return 'err';}})()"
            )
            try { web.evaluateJavascript(js, null) } catch (_: Throwable) {}
            // Allow JS animation time to run without blocking; UI thread loops
            val end = SystemClock.uptimeMillis() + durationMs + 250
            while (SystemClock.uptimeMillis() < end) {
                uiController.loopMainThreadForAtLeast(50)
            }
        }
    }

    private class WaitContentHeight(private val minHeight: Int, private val timeoutMs: Long) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "Wait for WebView.contentHeight >= $minHeight"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            val start = SystemClock.uptimeMillis()
            var last = -1
            while (SystemClock.uptimeMillis() - start < timeoutMs) {
                last = web.contentHeight
                if (last >= minHeight) return
                uiController.loopMainThreadForAtLeast(100)
            }
            throw AssertionError("Step05: WebView contentHeight stuck at $last")
        }
    }

    private class NudgeScrollAction : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
        override fun getDescription(): String = "Nudge scroll by +/- 1px to trigger observers"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            val js = (
                "(function(){try{" +
                    "var y=(window.scrollY||0); window.scrollTo(0, y+1); setTimeout(function(){ try{ window.scrollTo(0, y); }catch(e){} }, 50);" +
                    "return 'ok';" +
                "}catch(e){return 'err';}})()"
            )
            try { web.evaluateJavascript(js, null) } catch (_: Throwable) {}
            uiController.loopMainThreadForAtLeast(120)
        }
    }

    private fun handleConfirmExitIfShown(device: UiDevice, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var clicked = false
        while (SystemClock.uptimeMillis() < deadline) {
            val exitBtn = device.findObject(By.text("Exit"))
            if (exitBtn != null) {
                try {
                    exitBtn.click()
                    device.waitForIdle(200)
                    clicked = true
                    break
                } catch (_: Throwable) {
                    // retry until timeout
                }
            }
            // If dialog title is visible, keep waiting
            val title = device.findObject(By.text("Leave reader?"))
            if (title == null && exitBtn == null) {
                // Dialog not visible, likely already dismissed
                break
            }
            SystemClock.sleep(150)
        }
        return clicked
    }
}
