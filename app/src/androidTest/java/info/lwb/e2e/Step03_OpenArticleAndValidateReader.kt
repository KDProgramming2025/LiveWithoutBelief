/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.e2e

import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import info.lwb.e2e.TestLaunchUtils.bringAppToForeground
import org.junit.Assert.fail

class Step03_OpenArticleAndValidateReader {
    fun openFirstArticle_andVerifyReaderLoads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // Ensure foreground
    TestLog.mark("bring app to foreground - Step03")
        bringAppToForeground(instrumentation.targetContext.packageName, timeoutMs = 5_000L)

        // We should be on the Articles list from Step 2; if not, attempt to navigate: back to Home and back into first menu.
    TestLog.mark("waiting for 'Article card' on list")
    var hasArticles = device.wait(Until.hasObject(By.desc("Article card")), 5_000)
        if (!hasArticles) {
            // Give the articles list extra time first (avoid back which may exit app)
            hasArticles = device.wait(Until.hasObject(By.desc("Article card")), 10_000)
        }
        if (!hasArticles) {
            // Maybe we're already on Home; try to ensure Home is visible
            val appPkg = instrumentation.targetContext.packageName
            TestLog.mark("articles not visible - trying to recover to Home")
            var homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), 3_000)
            var attempts = 0
            while (!homeVisible && attempts < 5) {
                // Try back to unwind any deep screen
                TestLog.mark("pressBack for recovery attempt ${attempts+1}")
                device.pressBack()
                Thread.sleep(250)
                // If we left the app, bring it back
                if (device.currentPackageName != appPkg) {
                    bringAppToForeground(appPkg, timeoutMs = 5_000L)
                }
                homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), 2_000)
                attempts++
            }
            if (!homeVisible) {
                // Final attempt: foreground and wait longer
                bringAppToForeground(appPkg, timeoutMs = 5_000L)
                homeVisible = device.wait(Until.hasObject(By.desc("Home menu item")), 10_000)
            }
            val menus = device.findObjects(By.desc("Home menu item"))
            if (!homeVisible || menus.isEmpty()) {
                TestAbortState.markAborted()
                fail("Step03: Couldn't recover to Home to open Articles")
            }
            TestLog.mark("tapping first menu item from Home to reach list")
            menus.first().click()
            val listVisible = device.wait(Until.hasObject(By.desc("Article card")), 10_000)
            if (!listVisible) {
                TestAbortState.markAborted()
                fail("Step03: Articles list did not appear after tapping menu item")
            }
        }

        // Open the first article card
        val cards = device.findObjects(By.desc("Article card"))
        if (cards.isEmpty()) {
            TestAbortState.markAborted()
            fail("Step03: No Article card to open")
        }
    TestLog.mark("tapping first article card")
    cards.first().click()

        // Wait for WebView to appear and load content
        // We assert two things:
        // 1) The WebView URL contains "/web/articles/" (indexUrl pattern)
        // 2) There is at least one <p> paragraph element in the DOM
        // Avoid Espresso-Web flakiness by polling evaluateJavascript inside the WebView.
        // Wait for WebView presence longer and then assert URL via a direct WebView matcher
        TestLog.mark("waiting for WebView")
        device.wait(Until.hasObject(By.clazz(WebView::class.java)), 15_000)
        // Give the WebView a chance to layout content before JS polling
        TestLog.mark("wait for WebView content ready (height>0 or DOM ready)")
        onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForContentReadyAction(minHeight = 1, timeoutMs = 20_000))
        TestLog.mark("asserting WebView url contains /web/articles/")
        onView(isAssignableFrom(WebView::class.java))
            .check(matches(withWebViewUrlContaining("/web/articles/")))

        // Assert there is at least one real <p> element in the DOM (no fallbacks)
        TestLog.mark("asserting >=1 <p> in DOM")
        onView(isAssignableFrom(WebView::class.java))
            .perform(WaitForParagraphsAction(minCount = 1, timeoutMs = 30_000))

        // Done
    TestLog.mark("step03 complete")
    }

    private fun withWebViewUrlContaining(fragment: String) = object : BoundedMatcher<View, WebView>(WebView::class.java) {
        override fun describeTo(description: org.hamcrest.Description) {
            description.appendText("WebView url containing '")
            description.appendText(fragment)
            description.appendText("'")
        }
        override fun matchesSafely(item: WebView): Boolean {
            val url = item.url ?: return false
            return url.contains(fragment)
        }
    }

    private class WaitForParagraphsAction(
        private val minCount: Int,
        private val timeoutMs: Long,
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

        override fun getDescription(): String =
            "Wait up to ${timeoutMs}ms for >=${minCount} <p> elements in the WebView DOM (after ready)"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = SystemClock.uptimeMillis()
            var lastP = 0
            var lastReady = 0
            var sanityOk = -1 // -1 unknown, 0 failed, 1 ok

            // Strictly count <p> across document and any same-origin iframes
            while (SystemClock.uptimeMillis() - start < timeoutMs) {
                val resultRef = java.util.concurrent.atomic.AtomicReference<String?>()
                // First, sanity-check that JS engine responds
                if (sanityOk < 0) {
                    try {
                        val sanityRef = java.util.concurrent.atomic.AtomicReference<String?>()
                        webView.evaluateJavascript("(function(){try{return 1+1;}catch(e){return 'E';}})()", ValueCallback { v ->
                            sanityRef.set(v)
                        })
                        val sStart = SystemClock.uptimeMillis()
                        while (SystemClock.uptimeMillis() - sStart < 1000) {
                            val sraw = sanityRef.get()
                            if (sraw != null) {
                                val valStr = sraw.trim('"')
                                sanityOk = if (valStr == "2") 1 else 0
                                break
                            }
                            uiController.loopMainThreadForAtLeast(50)
                        }
                        if (sanityOk < 0) sanityOk = 0
                    } catch (_: Throwable) {
                        sanityOk = 0
                    }
                }
                // Ready flag + paragraph count (prefer main.article) including same-origin iframes
                val js = (
                    "(function(){\n" +
                        " function countIn(doc){\n" +
                        "  try{ if(!doc) return 0;\n" +
                        "   var root = doc.querySelector('main.article') || doc.body;\n" +
                        "   var c = root ? root.getElementsByTagName('p').length : 0;\n" +
                        "   var ifs = doc.getElementsByTagName('iframe');\n" +
                        "   for(var i=0;i<ifs.length;i++){\n" +
                        "     try{ var idoc = ifs[i].contentDocument || (ifs[i].contentWindow && ifs[i].contentWindow.document);\n" +
                        "          if(idoc){ c += countIn(idoc); } }catch(e){}\n" +
                        "   }\n" +
                        "   return c;\n" +
                        "  }catch(e){ return 0; }\n" +
                        " }\n" +
                        " try{\n" +
                        "  var ready = (document.readyState === 'complete') ? 1 : 0;\n" +
                        "  var p = countIn(document);\n" +
                        "  return (ready+':' + p);\n" +
                        " }catch(e){ return '0:0'; }\n" +
                    "})()"
                )
                try {
                    webView.evaluateJavascript(js, ValueCallback { value ->
                        resultRef.set(value)
                    })
                } catch (_: Throwable) {
                    resultRef.set(null)
                }

                // Wait briefly for JS evaluation to complete without blocking main thread
                val rStart = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - rStart < 1000) {
                    val got = resultRef.get()
                    if (got != null) break
                    uiController.loopMainThreadForAtLeast(50)
                }
                val raw = (resultRef.get() ?: "0:0").trim().replace("\"", "")
                val parts = raw.split(":")
                if (parts.size >= 2) {
                    lastReady = parts[0].toIntOrNull() ?: 0
                    lastP = parts[1].toIntOrNull() ?: 0
                } else {
                    lastReady = 0; lastP = 0
                }

                if (lastP >= minCount) {
                    return
                }
                // Give the page some breathing room
                uiController.loopMainThreadForAtLeast(300)
            }

            // Final debug snapshot to aid diagnostics without relaxing strictness
            var dbg: String? = null
            val dbgJs = (
                "(function(){\n" +
                    " try{\n" +
                    "  var root = document.querySelector('main.article') || document.body;\n" +
                    "  var ready = (document.readyState === 'complete') ? 1 : 0;\n" +
                    "  var p = root ? root.getElementsByTagName('p').length : 0;\n" +
                    "  var h = root ? root.querySelectorAll('h1,h2,h3,h4,h5,h6').length : 0;\n" +
                    "  var imgs = root ? root.getElementsByTagName('img').length : 0;\n" +
                    "  var a = root ? root.getElementsByTagName('a').length : 0;\n" +
                    "  var txt = (root && root.textContent) ? root.textContent.trim() : '';\n" +
                    "  var sample = txt.substring(0, 160).replace(/\\n/g,' ').replace(/\\s+/g,' ').slice(0,160);\n" +
                    "  var href = (typeof window !== 'undefined' && window.location && window.location.href) ? window.location.href : '';\n" +
                    "  var ensureType = (typeof window !== 'undefined' && window.lwbEnsureParagraphs) ? (typeof window.lwbEnsureParagraphs) : 'undef';\n" +
                    "  var rc = root ? (root.children ? root.children.length : -1) : -1;\n" +
                    "  var hasRoot = !!root;\n" +
                    "  return ready+':'+p+':'+h+':'+imgs+':'+a+':'+sample+':href='+href+':ensure='+ensureType+':rc='+rc+':hasRoot='+hasRoot;\n" +
                    " }catch(e){ return '0:0:0:0:0:'; }\n" +
                "})()"
            )
            val dbgRef = java.util.concurrent.atomic.AtomicReference<String?>()
            try {
                webView.evaluateJavascript(dbgJs, ValueCallback { value ->
                    dbgRef.set(value)
                })
            } catch (_: Throwable) {
                dbgRef.set("eval-error")
            }
            val dStart = SystemClock.uptimeMillis()
            while (SystemClock.uptimeMillis() - dStart < 1500) {
                val got = dbgRef.get()
                if (got != null) break
                uiController.loopMainThreadForAtLeast(50)
            }
            val dbgRaw = (dbgRef.get() ?: "no-callback").trim().trim('"')
            val sanityStr = when (sanityOk) { -1 -> "unknown"; 0 -> "fail"; 1 -> "ok"; else -> sanityOk.toString() }
            throw AssertionError("Expected >=$minCount <p> elements, but saw p=$lastP (ready=$lastReady) within ${timeoutMs}ms; sanity=$sanityStr; dbg=$dbgRaw")
        }
    }

    private class WaitForContentReadyAction(
        private val minHeight: Int,
        private val timeoutMs: Long,
    ) : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

        override fun getDescription(): String =
            "Wait up to ${timeoutMs}ms for WebView content ready (contentHeight >= ${minHeight} or DOM ready with scrollHeight>0 or paragraphs>0)"

        override fun perform(uiController: UiController, view: View) {
            val webView = view as WebView
            val start = SystemClock.uptimeMillis()
            var lastH = -1
            var lastProbe = ""
            while (SystemClock.uptimeMillis() - start < timeoutMs) {
                try {
                    val h = webView.contentHeight
                    lastH = h
                    if (h >= minHeight) return
                } catch (_: Throwable) {
                    // ignore
                }

                val ref = java.util.concurrent.atomic.AtomicReference<String?>()
                val js = (
                    "(function(){try{" +
                        "var ready = (document.readyState==='complete'||document.readyState==='interactive')?1:0;" +
                        "var p = (document.querySelector('main.article')||document.body).getElementsByTagName('p').length;" +
                        "var sh = (document.body && document.body.scrollHeight) ? document.body.scrollHeight : 0;" +
                        "return ready+':'+p+':'+sh;" +
                    "}catch(e){return '0:0:0';}})()"
                )
                try { webView.evaluateJavascript(js, ValueCallback { v -> ref.set(v) }) } catch (_: Throwable) { ref.set(null) }
                val t0 = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - t0 < 800) {
                    val got = ref.get()
                    if (got != null) break
                    uiController.loopMainThreadForAtLeast(50)
                }
                val raw = (ref.get() ?: "'0:0:0'").trim('"')
                lastProbe = raw
                val parts = raw.split(":")
                val ready = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val p = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val sh = parts.getOrNull(2)?.toIntOrNull() ?: 0
                if (ready == 1 && (sh > 0 || p > 0)) return

                uiController.loopMainThreadForAtLeast(250)
            }
            throw AssertionError("WebView content not ready within ${timeoutMs}ms (contentHeight=$lastH, probe=$lastProbe)")
        }
    }

    // no-op
}
