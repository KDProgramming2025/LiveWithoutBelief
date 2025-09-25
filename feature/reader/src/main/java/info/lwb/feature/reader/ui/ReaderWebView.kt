/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.graphics.Color
import android.os.Build

@Composable
fun ArticleWebView(
    htmlBody: String? = null,
    baseUrl: String? = null,
    url: String? = null,
    injectedCss: String? = null,
    fontScale: Float? = null,
    lineHeight: Float? = null,
    backgroundColor: String? = null,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onScrollChanged: ((scrollY: Int) -> Unit)? = null,
    initialScrollY: Int? = null,
) {
    var ready by remember(url, htmlBody) { mutableStateOf(false) }
    var firstLoad by remember(url, htmlBody) { mutableStateOf(true) }
    // Track last applied values so we don't re-apply typography when only background changes
    var lastFontScale by remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    var lastLineHeight by remember(url, htmlBody) { mutableStateOf<Float?>(null) }
        var restoreActive by remember(url, htmlBody) { mutableStateOf(false) }
        var restoreTarget by remember(url, htmlBody) { mutableStateOf<Int?>(null) }
    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                alpha = if (ready) 1f else 0f
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                // Disable algorithmic darkening/force dark so our background colors are honored
                try {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                    // Also set platform forceDark flag explicitly on Android Q+ for extra assurance
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        this.settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                } catch (_: Throwable) { }
                // Set the WebView background to selected reader background in case page is transparent
                try {
                    val bgStr = backgroundColor
                    if (!bgStr.isNullOrBlank()) this.setBackgroundColor(Color.parseColor(bgStr))
                } catch (_: Throwable) { this.setBackgroundColor(Color.WHITE) }
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
                    if (scrollX != 0) v.scrollTo(0, scrollY)
                    if (!restoreActive) onScrollChanged?.invoke(scrollY)
                }
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                // Keep a mutable reference to the latest CSS so intercept can serve updates
                val cssRef = arrayOf(injectedCss)
                // Deterministic scroll restore with a few retries to overcome late layout shifts (images/fonts)
                val restoreDelays = longArrayOf(0, 50, 150, 350, 700, 1200, 2000)
                fun startRestore(target: Int) {
                    if (target <= 0) return
                    restoreTarget = target
                    restoreActive = true
                    fun step(i: Int) {
                        if (restoreTarget != target) { restoreActive = false; return }
                        this@apply.scrollTo(0, target)
                        if (i >= restoreDelays.lastIndex) { restoreActive = false; return }
                        this@apply.postDelayed({
                            val current = this@apply.scrollY
                            val close = kotlin.math.abs(current - target) <= 12
                            if (close) { restoreActive = false } else { step(i + 1) }
                        }, restoreDelays[i + 1])
                    }
                    this@apply.post { step(0) }
                }
                fun loadAsset(path: String): String {
                    val am = context.assets
                    am.open(path).use { input ->
                        return BufferedReader(InputStreamReader(input)).readText()
                    }
                }
                // Expose cssRef via tag so updates can mutate it without recreating the WebView
                this.tag = cssRef
                val clampJs by lazy { loadAsset("webview/inject_clamp.js") }
                val themeJs by lazy { loadAsset("webview/inject_theme.js") }
                val domHelpersJs by lazy { loadAsset("webview/dom_helpers.js") }
                val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
                isLongClickable = false
                setOnLongClickListener { true }
                setDownloadListener { _, _, _, _, _ -> }
                var downX = 0f; var downY = 0f; var downTs = 0L
                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                setOnTouchListener { _, ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; downTs = ev.eventTime }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dt = ev.eventTime - downTs
                            val dx = ev.x - downX
                            val dy = ev.y - downY
                            if (dt in 1..250 && (dx*dx + dy*dy) <= (slop * slop)) onTap?.invoke()
                        }
                    }
                    false
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)

                        if (firstLoad) {
                            ready = false
                            this@apply.alpha = 0f
                        }
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val u = url ?: return false
                        if (isInlineContent && u.startsWith("lwb-assets://")) return false
                        return openExternal(u)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val u = request?.url?.toString() ?: return false
                        if (isInlineContent && u.startsWith("lwb-assets://")) return false
                        if (request?.isForMainFrame == true) return openExternal(u)
                        return false
                    }
                    private fun openExternal(u: String): Boolean = try {
                        if (u.startsWith("intent://")) { val intent = Intent.parseUri(u, Intent.URI_INTENT_SCHEME); context.startActivity(intent); true }
                        else { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u)); context.startActivity(intent); true }
                    } catch (_: Throwable) { false }

                    private fun intercept(u: String?): WebResourceResponse? {
                        val url = u ?: return null
                        // Serve same-origin theme CSS via a synthetic path to bypass CSP inline restrictions
                        // Match https?://<same-host>/lwb-theme.css (ignore query)
                        try {
                            val current = this@apply.url
                            if (!current.isNullOrBlank()) {
                                val cur = android.net.Uri.parse(current)
                                val req = android.net.Uri.parse(url)
                                val isThemeReq = req.path?.endsWith("/lwb-theme.css") == true
                                val sameHost = cur.host == req.host && cur.scheme == req.scheme
                                if (isThemeReq && sameHost) {
                                    val bytes = (cssRef[0] ?: "").toByteArray(Charsets.UTF_8)
                                    return WebResourceResponse("text/css", "utf-8", java.io.ByteArrayInputStream(bytes))
                                }
                            }
                        } catch (_: Throwable) { }
                        if (isInlineContent && url.startsWith("lwb-assets://")) {
                            val path = url.removePrefix("lwb-assets://")
                            return try {
                                val mime = when {
                                    path.endsWith(".js") -> "application/javascript"
                                    path.endsWith(".css") -> "text/css"
                                    else -> "application/octet-stream"
                                }
                                val stream = context.assets.open(path)
                                WebResourceResponse(mime, "utf-8", stream)
                            } catch (_: Throwable) { null }
                        }
                        return null
                    }
                    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = intercept(url) ?: super.shouldInterceptRequest(view, url)
                    override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
                        val u = request?.url?.toString(); return intercept(u) ?: super.shouldInterceptRequest(view, request)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val css = injectedCss
                        // Prefer external same-origin stylesheet to bypass CSP; insert/update link for URL content only
                        if (!isInlineContent) {
                            // Load DOM helper functions then call them
                            evaluateJavascript(domHelpersJs, null)
                            evaluateJavascript("lwbEnsureLightMeta()", null)
                            evaluateJavascript("lwbEnsureThemeLink()", null)
                            evaluateJavascript("lwbEnsureBgOverride()", null)
                            evaluateJavascript("lwbDisableColorSchemeDarkening()", null)
                        }
                        // Fallback inline helper if external link fails for any reason
                        evaluateJavascript(themeJs, null)
                        if (!css.isNullOrBlank()) {
                            val b64 = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                            evaluateJavascript("window.lwbApplyThemeCss('" + b64 + "')", null)
                        }
                        // Apply runtime variables (font size, line height, background)
                        val fs = fontScale
                        val lh = lineHeight
                        val bg = backgroundColor ?: ""
                        // Only pass typography values if they actually changed
                        val fsChanged = fs != null && fs != lastFontScale
                        val lhChanged = lh != null && lh != lastLineHeight
                        val fsArg = if (fsChanged) fs.toString() else "undefined"
                        val lhArg = if (lhChanged) lh.toString() else "undefined"
                        evaluateJavascript("lwbApplyReaderVars(" + fsArg + ", " + lhArg + ", '" + bg + "')") { _ ->
                            // Mark ready after first load is fully styled
                            if (firstLoad) {
                                ready = true
                                this@apply.alpha = 1f
                                firstLoad = false
                                // Kick off robust scroll restore after initial style pass
                                try { startRestore(initialScrollY ?: 0) } catch (_: Throwable) { }
                            }
                            if (fsChanged) lastFontScale = fs
                            if (lhChanged) lastLineHeight = lh
                        }
                        if (isInlineContent) evaluateJavascript(clampJs, null)
                    }
                }
                if (!url.isNullOrBlank()) {
                    loadUrl(url)
                } else {
                    val finalHtml = if (!htmlBody.isNullOrBlank() && !injectedCss.isNullOrBlank()) {
                        mergeHtmlAndCss(htmlBody, injectedCss)
                    } else htmlBody.orEmpty()
                    loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
                }
            }
        },
        update = { webView ->
            if (!url.isNullOrBlank()) {
                // For URL content, do not reload unless URL changed; just inject CSS if provided.
                // Ensure helper script, then call function.
                val css = injectedCss
                // update cssRef so intercept serves latest
                (webView.tag as? Array<String?>)?.let { it[0] = css }
                // Update the mutable CSS ref captured by client for intercept
                try {
                    // NOP: captured ref updated by recreating client on recomposition; for safety we also refresh link
                } catch (_: Throwable) { }
                // Refresh external link with cache buster to pull latest CSS via intercept
                // Load helpers and refresh theme link via helper
                val domHelpers = try { loadAssetText(webView.context, "webview/dom_helpers.js") } catch (_: Throwable) { "" }
                if (domHelpers.isNotBlank()) {
                    webView.evaluateJavascript(domHelpers, null)
                }
                webView.evaluateJavascript("lwbEnsureLightMeta()", null)
                webView.evaluateJavascript("lwbRefreshThemeLink()", null)
                webView.evaluateJavascript("lwbEnsureBgOverride()", null)
                webView.evaluateJavascript("lwbDisableColorSchemeDarkening()", null)
                // Keep inline helper path for immediate application if external blocked momentarily
                fun loadAssetText(ctx: android.content.Context, path: String): String {
                    return try {
                        ctx.assets.open(path).use { input ->
                            BufferedReader(InputStreamReader(input)).readText()
                        }
                    } catch (_: Throwable) { "" }
                }
                val themeJsCode = try { loadAssetText(webView.context, "webview/inject_theme.js") } catch (_: Throwable) { "" }
                if (themeJsCode.isNotBlank()) {
                    webView.evaluateJavascript(themeJsCode, null)
                }
                if (!css.isNullOrBlank()) {
                    val b64 = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    webView.evaluateJavascript("window.lwbApplyThemeCss('" + b64 + "')", null)
                }
                // Re-apply runtime variables on updates
                val fs = fontScale
                val lh = lineHeight
                val bg = backgroundColor ?: ""
                // Only pass typography values if they actually changed
                val fsChanged = fs != null && fs != lastFontScale
                val lhChanged = lh != null && lh != lastLineHeight
                val fsArg = if (fsChanged) fs.toString() else "undefined"
                val lhArg = if (lhChanged) lh.toString() else "undefined"
                webView.evaluateJavascript("lwbApplyReaderVars(" + fsArg + ", " + lhArg + ", '" + bg + "')", null)
                if (fsChanged) lastFontScale = fs
                if (lhChanged) lastLineHeight = lh
                    // If WebView is ready and a desired scroll is available, perform a short restore sequence
                    val desired = initialScrollY ?: 0
                    if (ready && desired > 0) {
                        val current = webView.scrollY
                        val isNewTarget = restoreTarget != desired
                        val far = kotlin.math.abs(current - desired) > 12
                        if (isNewTarget || (!restoreActive && far)) {
                            restoreTarget = desired
                            restoreActive = true
                            val delays = longArrayOf(0, 100, 300, 800, 1600)
                            fun step(i: Int) {
                                if (restoreTarget != desired) { restoreActive = false; return }
                                webView.scrollTo(0, desired)
                                if (i >= delays.lastIndex) { restoreActive = false; return }
                                webView.postDelayed({
                                    val cur = webView.scrollY
                                    val close = kotlin.math.abs(cur - desired) <= 12
                                    if (close) { restoreActive = false } else { step(i + 1) }
                                }, delays[i + 1])
                            }
                            webView.post { step(0) }
                        }
                    }
                if (webView.url != url) webView.loadUrl(url)
            } else {
                val css = injectedCss
                val finalHtml = if (!htmlBody.isNullOrBlank() && !css.isNullOrBlank()) {
                    mergeHtmlAndCss(htmlBody, css)
                } else htmlBody.orEmpty()
                webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
            }
        }
    )
    if (!ready) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
    }
}
