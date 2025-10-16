package info.lwb.startup

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.model.Atoms.getCurrentUrl
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.model.Atoms.script
import androidx.test.espresso.web.model.Atoms.castOrDie
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.espresso.web.sugar.Web.onWebView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.hamcrest.Matchers.allOf
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the time from a cold app launch until the Home screen is fully loaded (first success state).
 * Detection is based on the presence of the ActionRail with contentDescription "Home quick actions".
 *
 * This single test is executed in both phases of the local two-phase run:
 *  - Upgrade phase: without clearing data (existing state)
 *  - Fresh phase: after clearing app data (performed by Gradle task)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class HomeFirstLoadTimingTest {

    @get:org.junit.Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        // Initialize Hilt test components before launching any Activity
        hiltRule.inject()
    }

    @Test
    fun coldStart_untilHomeLoaded_reportsElapsedMs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val pkg = ctx.packageName
        val device = UiDevice.getInstance(instrumentation)
        // Return to launcher to reduce interference from previous state
        try { device.pressHome() } catch (_: Exception) {}

        // Prefer a robust foreground launch using ActivityManager via shell to avoid launcher quirks
        val startMs = SystemClock.elapsedRealtime()
        val amStartPrimary = "am start -W -n $pkg/info.lwb.MainActivity"
        val amStartFallback = "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $pkg/info.lwb.MainActivity"
        val amOut1 = try { device.executeShellCommand(amStartPrimary) } catch (_: Exception) { "" }
        // If first attempt didn't bring us to foreground quickly, try a fallback form
        var inForeground = device.wait(Until.hasObject(By.pkg(pkg)), 12_000)
        val amOut2 = if (!inForeground) {
            try { device.executeShellCommand(amStartFallback) } catch (_: Exception) { "" }
        } else ""
        if (!inForeground) {
            // Re-check without depth constraint; some OEMs overlay system windows
            inForeground = device.wait(Until.hasObject(By.pkg(pkg)), 8_000)
        }

        // Then wait until we observe the Home success overlay (ActionRail) available
        val homeReadyDesc = "Home quick actions"
    val ready = inForeground && device.wait(Until.hasObject(By.desc(homeReadyDesc)), 60_000)
        val endMs = SystemClock.elapsedRealtime()
        val elapsed = endMs - startMs

        Log.i("StartupHomeTiming", "Home first load time: ${elapsed}ms (ready=$ready)")
        println("[StartupHomeTiming] Home first load time: ${elapsed}ms (ready=$ready)")

        if (!ready) {
            val curPkg = try { device.currentPackageName } catch (_: Exception) { "unknown" }
            if (amOut1.isNotBlank()) println("[StartupHomeTiming] amStart(primary) output: ${amOut1.trim().take(300)}")
            if (amOut2.isNotBlank()) println("[StartupHomeTiming] amStart(fallback) output: ${amOut2.trim().take(300)}")
            println("[StartupHomeTiming] Not ready: appForeground=${inForeground}; currentPackage=$curPkg; waitedMs=$elapsed")
        }
        assertTrue("Timed out waiting for Home to be ready (no '$homeReadyDesc' within 60s; inForeground=$inForeground)", ready)

        // After home is ready, validate that menu items are shown and count them.
        // We rely on Compose semantics contentDescription set to "Home menu item" on each card.
        val menuItemSelector = By.desc("Home menu item")
        val appeared = device.wait(Until.hasObject(menuItemSelector), 10_000)
        val menuItems = if (appeared) device.findObjects(menuItemSelector) else emptyList()
        Log.i("StartupHomeTiming", "Home menu items visible: ${menuItems.size}")
        println("[StartupHomeTiming] Home menu items visible: ${menuItems.size}")
        assertTrue("Expected at least 1 menu item on Home, found ${menuItems.size}", menuItems.isNotEmpty())

        // For each menu item, validate it has exactly one icon and exactly one title, and the icon is visible
        var allValid = true
        var firstInvalidIndex = -1
        menuItems.forEachIndexed { index, item ->
            // Scope searches to descendants of the menu item where possible.
            // UiAutomator's BySelector doesn't directly support scoping, so we approximate by
            // retrieving all matching nodes globally and filtering by bounds containment.
            val itemBounds = item.visibleBounds
            val iconSelectors = arrayOf(
                By.desc("Home menu icon - image"),
                By.desc("Home menu icon - placeholder"),
            )
            val icons = iconSelectors.flatMap { selector ->
                device.findObjects(selector).filter { it.visibleBounds.intersect(itemBounds) }
            }
            val titles = device.findObjects(By.desc("Home menu title")).filter { it.visibleBounds.intersect(itemBounds) }

            val iconNode = icons.firstOrNull()
            val iconVisible = iconNode != null && iconNode.visibleBounds.width() > 0 && iconNode.visibleBounds.height() > 0
            val valid = (icons.size == 1) && (titles.size == 1) && iconVisible
            if (!valid && allValid) {
                firstInvalidIndex = index
            }
            allValid = allValid && valid
        }
        Log.i("StartupHomeTiming", "Home menu items structure valid: ${allValid}")
        println("[StartupHomeTiming] Home menu items structure valid: ${allValid}; firstInvalidIndex=${firstInvalidIndex}")
        assertTrue("Each Home menu item must have exactly one icon and one title, and the icon must be visible", allValid)

        // Click the first menu item to open its articles list
        val firstMenu = menuItems.first()
        firstMenu.click()

        // Wait for the Articles list screen to load and verify at least one article card is visible
        val articleCardSelector = By.desc("Article card")
        val listAppeared = device.wait(Until.hasObject(articleCardSelector), 30_000)
        val articles = if (listAppeared) device.findObjects(articleCardSelector) else emptyList()
        Log.i("StartupHomeTiming", "Articles visible after opening first menu: ${articles.size}")
        println("[StartupHomeTiming] Articles visible: ${articles.size}")
        assertTrue("Expected at least 1 article after opening first menu, found ${articles.size}", articles.isNotEmpty())

        // Click the first article card
        val firstArticle = articles.first()
        firstArticle.click()

        // Verify WebView has loaded the article's URL and that body text length > 50
        // Check current URL looks like an article page (http(s) and contains '/articles/')
        onWebView()
            .forceJavascriptEnabled()
            .check(webMatches(getCurrentUrl(), allOf(startsWith("http"), containsString("/articles/"))))

        // Check body text length > 50 characters
        onWebView()
            .forceJavascriptEnabled()
            .withElement(findElement(Locator.TAG_NAME, "body"))
            .check(webMatches(getText(), hasTextLongerThan(50)))

    // Log that content check passed
    Log.i("StartupHomeTiming", "Reader WebView URL looks like article and body length > 50 (validated)")
    println("[StartupHomeTiming] Reader WebView content validated: urlLooksLikeArticle=true; bodyLength>50=true")

        // Open reader appearance sheet and validate applied changes reflect in WebView computed styles.
        ensureReaderActionsAndOpenAppearance(device)

        // Increase font to A++ (1.2x) and verify computed font-size on <html> is between 19 and 20 px
        clickByTextWithScroll(device, "A++")
        SystemClock.sleep(300)
        onWebView()
            .forceJavascriptEnabled()
            .check(
                webMatches(
                    script(
                        "return window.getComputedStyle(document.documentElement).fontSize",
                        castOrDie(String::class.java),
                    ),
                    hasPixelBetween(19.0f, 20.0f),
                ),
            )

        // Set line height to Loose (1.8x) and verify computed line-height in pixels on <body> ~34-36px
        clickByTextWithScroll(device, "Loose")
        SystemClock.sleep(300)
        onWebView()
            .forceJavascriptEnabled()
            .check(
                webMatches(
                    script(
                        "return window.getComputedStyle(document.body).lineHeight",
                        castOrDie(String::class.java),
                    ),
                    hasPixelBetween(34.0f, 36.0f),
                ),
            )

        // Change background to Night and verify CSS var --lwb-bg is set to #000000 (our Night hex)
        clickBackgroundSwatchAboveLabel(device, "Night")
        SystemClock.sleep(400)
        waitForWebCssVarContains(
            varName = "--lwb-bg",
            expectedSubstring = "#000000",
            timeoutMs = 5_000,
        )
        Log.i("StartupHomeTiming", "Reader appearance changes validated: font~19.2px; line~34.56px; bg black")
        println("[StartupHomeTiming] Reader appearance changes validated: font~19.2px; line~34.56px; bg=night")
    }

    // Hamcrest matcher to assert string length > min
    private fun hasTextLongerThan(min: Int): Matcher<String> = object : TypeSafeMatcher<String>() {
        override fun describeTo(description: Description) {
            description.appendText("a string with length greater than ").appendValue(min)
        }
        override fun matchesSafely(item: String?): Boolean {
            return item != null && item.trim().length > min
        }
        override fun describeMismatchSafely(item: String?, mismatchDescription: Description) {
            mismatchDescription.appendText("was ")
                .appendValue(item?.length ?: 0)
                .appendText(" characters: ")
                .appendText(item ?: "null")
        }
    }

    // Hamcrest matcher to assert a CSS pixel value string (like "19.2px" or "34px") falls in [min,max)
    private fun hasPixelBetween(min: Float, max: Float): Matcher<String> = object : TypeSafeMatcher<String>() {
        override fun describeTo(description: Description) {
            description.appendText("a CSS pixel value between ")
                .appendValue(min)
                .appendText(" and ")
                .appendValue(max)
        }
        override fun matchesSafely(item: String?): Boolean {
            if (item.isNullOrBlank()) return false
            val v = item.trim().removeSuffix("px").toFloatOrNull() ?: return false
            return v >= min && v < max
        }
        override fun describeMismatchSafely(item: String?, mismatchDescription: Description) {
            mismatchDescription.appendText("was ").appendValue(item)
        }
    }

    // Helpers for reader appearance interactions
    private fun ensureReaderActionsAndOpenAppearance(device: UiDevice) {
        // Ensure the floating actions toggle is visible; tap center if needed to reveal it
        var btn = device.findObject(By.desc("Reader actions"))
        if (btn == null) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            device.wait(Until.hasObject(By.desc("Reader actions")), 2_000)
            btn = device.findObject(By.desc("Reader actions"))
        }
        btn?.click()
        // Tap the "Appearance" action in the bottom sheet and wait for sheet title
        clickByText(device, "Appearance")
        device.wait(Until.hasObject(By.text("Reader appearance")), 3_000)
    }

    private fun clickByText(device: UiDevice, text: String) {
        val obj = device.wait(Until.findObject(By.text(text)), 3_000)
        obj?.click()
    }

    private fun clickByTextWithScroll(device: UiDevice, text: String) {
        // Try to find immediately
        var obj = device.findObject(By.text(text))
        if (obj == null) {
            // Attempt a few swipes up within the bottom sheet area to reveal more options
            val w = device.displayWidth
            val h = device.displayHeight
            val startX = w / 2
            // Swipe in the lower 70% of the screen to avoid hitting toolbars
            val startY = (h * 0.80).toInt()
            val endY = (h * 0.35).toInt()
            repeat(4) {
                device.swipe(startX, startY, startX, endY, /*steps*/ 20)
                device.waitForIdle(200)
                obj = device.findObject(By.text(text))
                if (obj != null) return@repeat
            }
        }
        if (obj == null) {
            // Final attempt with wait in case layout animates
            obj = device.wait(Until.findObject(By.text(text)), 1_000)
        }
        obj?.click()
    }

    private fun clickBackgroundSwatchAboveLabel(device: UiDevice, label: String) {
        // Ensure label is visible; scroll if needed
        var obj = device.findObject(By.text(label))
        if (obj == null) {
            clickByTextWithScroll(device, label) // reveal if needed
            obj = device.findObject(By.text(label))
        }
        val bounds = obj?.visibleBounds ?: return
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val swatchSizePx = (32f * density).toInt()
        val gapPx = (4f * density).toInt()
        val x = bounds.centerX()
        val y = bounds.top - gapPx - (swatchSizePx / 2)
        if (y > 0) {
            device.click(x, y)
        } else {
            obj.click() // fallback
        }
    }

    private fun waitForWebCssVarContains(varName: String, expectedSubstring: String, timeoutMs: Long = 4_000) {
        val start = SystemClock.elapsedRealtime()
        val scriptJs = "return (window.getComputedStyle(document.documentElement).getPropertyValue('" + varName + "') || window.getComputedStyle(document.body).getPropertyValue('" + varName + "') || '').trim().toLowerCase()"
        val expected = expectedSubstring.lowercase()
        var success = false
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            try {
                onWebView()
                    .forceJavascriptEnabled()
                    .check(
                        webMatches(
                            script(scriptJs, castOrDie(String::class.java)),
                            containsString(expected),
                        ),
                    )
                success = true
                break
            } catch (_: Throwable) {
                SystemClock.sleep(250)
            }
        }
        if (!success) {
            // One final check to throw an assertion with the actual value for debugging
            onWebView()
                .forceJavascriptEnabled()
                .check(
                    webMatches(
                        script(scriptJs, castOrDie(String::class.java)),
                        containsString(expected),
                    ),
                )
        }
    }
}
