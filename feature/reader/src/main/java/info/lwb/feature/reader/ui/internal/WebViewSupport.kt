/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui.internal

import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Lightweight container for JS snippets loaded from assets. Splitting keeps primary composable short.
 */
internal data class WebViewAssetScripts(
    val clampJs: String,
    val themeJs: String,
    val domHelpersJs: String,
)

internal fun buildInlineHtml(body: String?, css: String?): String = if (!body.isNullOrBlank() && !css.isNullOrBlank()) {
    mergeHtmlAndCss(body, css)
} else {
    body.orEmpty()
}

internal fun numArg(v: Float?): String = if (v == null) {
    "undefined"
} else {
    String.format(Locale.US, "%.1f", v)
}

internal fun WebView.safeLoadAsset(path: String): String = try {
    context.assets.open(path).use { input ->
        BufferedReader(InputStreamReader(input)).readText()
    }
} catch (_: Throwable) {
    ""
}

internal fun WebView.configureBaseSettings(backgroundColor: String?) {
    alpha = 0f
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            settings.forceDark = WebSettings.FORCE_DARK_OFF
        }
    } catch (_: Throwable) {
        // ignored (feature probing)
    }
    try {
        if (!backgroundColor.isNullOrBlank()) {
            setBackgroundColor(Color.parseColor(backgroundColor))
        } else {
            setBackgroundColor(Color.WHITE)
        }
    } catch (_: Throwable) {
        setBackgroundColor(Color.WHITE)
    }
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    isLongClickable = false
    setOnLongClickListener { true }
    setDownloadListener { _, _, _, _, _ -> }
}

internal fun WebView.setupTouchHandlers(onTap: (() -> Unit)?) {
    if (onTap == null) {
        return
    }
    var downX = 0f
    var downY = 0f
    var downTs = 0L
    val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTs = ev.eventTime
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dt = ev.eventTime - downTs
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dt in 1..250 && (dx * dx + dy * dy) <= (slop * slop)) {
                    onTap()
                }
            }
        }
        false
    }
}

internal fun WebView.setupScrollHandler(enabled: () -> Boolean, onScroll: (Int) -> Unit, onAnchor: (String) -> Unit) {
    var lastAnchorTs = 0L
    setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
        if (scrollX != 0) {
            v.scrollTo(0, scrollY)
        }
        if (!enabled()) {
            return@setOnScrollChangeListener
        }
        onScroll(scrollY)
        val now = System.currentTimeMillis()
        if (now - lastAnchorTs > 200) {
            lastAnchorTs = now
            try {
                evaluateJavascript("(window.lwbGetViewportAnchor && window.lwbGetViewportAnchor())||''") { res ->
                    try {
                        val anchor = res?.trim('"')?.replace("\\n", "") ?: ""
                        if (anchor.isNotBlank()) {
                            onAnchor(anchor)
                        }
                    } catch (_: Throwable) {
                        // ignore js errors
                    }
                }
            } catch (_: Throwable) {
                // ignore js eval errors
            }
        }
    }
}

internal fun WebView.postRestore(target: Int, onDone: () -> Unit) {
    val delays = longArrayOf(0, 50, 150, 350, 700, 1200, 2000)
    fun step(i: Int) {
        scrollTo(0, target)
        if (i >= delays.lastIndex) {
            return onDone()
        }
        postDelayed({
            val close = kotlin.math.abs(scrollY - target) <= 12
            if (close) {
                onDone()
            } else {
                step(i + 1)
            }
        }, delays[i + 1])
    }
    post { step(0) }
}

internal class ArticleClient(
    private val isInlineContent: Boolean,
    private val cssRef: Array<String?>,
    private val injectedCss: String?,
    private val initialAnchor: String?,
    private val initialScrollY: Int,
    private val fontScale: Float?,
    private val lineHeight: Float?,
    private val backgroundColor: String?,
    private val onFirstReady: () -> Unit,
    private val firstLoad: () -> Boolean,
    private val setReadyHidden: () -> Unit,
    private val startRestore: (Int) -> Unit,
    private val setLastValues: (Float?, Float?) -> Unit,
    private val assets: WebViewAssetScripts,
    private val evaluate: (String) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (firstLoad()) {
            setReadyHidden()
        }
    }
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val u = url ?: return false
        if (isInlineContent && u.startsWith("lwb-assets://")) {
            return false
        }
        return openExternal(view, u)
    }
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val u = request?.url?.toString() ?: return false
        if (isInlineContent && u.startsWith("lwb-assets://")) {
            return false
        }
        return if (request?.isForMainFrame == true) {
            openExternal(view, u)
        } else {
            false
        }
    }
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? =
        intercept(view, url) ?: super.shouldInterceptRequest(view, url)
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val u = request?.url?.toString()
        return intercept(view, u) ?: super.shouldInterceptRequest(view, request)
    }
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null) {
            return
        }
        val css = injectedCss
        if (!isInlineContent) {
            evaluate(assets.domHelpersJs)
            evaluate("lwbEnsureLightMeta()")
            evaluate("lwbEnsureThemeLink()")
            evaluate("lwbEnsureBgOverride()")
            evaluate("lwbDisableColorSchemeDarkening()")
        }
        evaluate(assets.themeJs)
        if (!css.isNullOrBlank()) {
            val b64 = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            evaluate("window.lwbApplyThemeCss('$b64')")
        }
        val fsArg = numArg(fontScale)
        val lhArg = numArg(lineHeight)
        val bg = backgroundColor ?: ""
        evaluate("lwbApplyReaderVars($fsArg, $lhArg, '$bg')")
        if (firstLoad()) {
            onFirstReady()
            var usedAnchor = false
            try {
                if (!initialAnchor.isNullOrBlank()) {
                    usedAnchor = true
                    val escaped = org.json.JSONObject.quote(initialAnchor)
                    evaluate("(window.lwbScrollToAnchor && window.lwbScrollToAnchor($escaped))")
                }
            } catch (_: Throwable) {
                // ignore anchor errors
            }
            if (!usedAnchor) {
                startRestore(initialScrollY)
            }
        }
        setLastValues(fontScale, lineHeight)
        if (isInlineContent) {
            evaluate(assets.clampJs)
        }
    }
    private fun intercept(view: WebView?, url: String?): WebResourceResponse? {
        val u = url ?: return null
        try {
            val current = view?.url
            if (!current.isNullOrBlank()) {
                val cur = Uri.parse(current)
                val req = Uri.parse(u)
                val isThemeReq = req.path?.endsWith("/lwb-theme.css") == true
                val sameHost = cur.host == req.host && cur.scheme == req.scheme
                if (isThemeReq && sameHost) {
                    val bytes = (cssRef[0] ?: "").toByteArray(Charsets.UTF_8)
                    return WebResourceResponse("text/css", "utf-8", java.io.ByteArrayInputStream(bytes))
                }
            }
        } catch (_: Throwable) {
            // ignore request parse errors
        }
        if (isInlineContent && u.startsWith("lwb-assets://")) {
            val path = u.removePrefix("lwb-assets://")
            return try {
                val mime = when {
                    path.endsWith(".js") -> "application/javascript"
                    path.endsWith(".css") -> "text/css"
                    else -> "application/octet-stream"
                }
                val stream = view?.context?.assets?.open(path)
                if (stream != null) {
                    WebResourceResponse(mime, "utf-8", stream)
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }
    private fun openExternal(view: WebView?, u: String): Boolean = try {
        val ctx = view?.context ?: return false
        if (u.startsWith("intent://")) {
            val intent = android.content.Intent.parseUri(u, android.content.Intent.URI_INTENT_SCHEME)
            ctx.startActivity(intent)
            true
        } else {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u))
            ctx.startActivity(intent)
            true
        }
    } catch (_: Throwable) {
        false
    }
}
