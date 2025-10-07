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
import info.lwb.feature.reader.ui.mergeHtmlAndCss
import info.lwb.core.common.log.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

// region logging
private const val SCROLL_PREFIX = "Scroll:"
private const val LOG_TAG = "ReaderWeb"
// endregion

/**
 * Lightweight container for JS snippets loaded from assets. Splitting keeps primary composable short.
 */
internal data class WebViewAssetScripts(val clampJs: String, val themeJs: String, val domHelpersJs: String)

// region internal constants
private const val THEME_CSS_PATH_SUFFIX = "/lwb-theme.css"
private const val ASSET_SCHEME_PREFIX = "lwb-assets://"
private const val RESTORE_CLOSE_DISTANCE_PX = 12
private const val TAP_MAX_DURATION_MS = 250L
private const val TAP_MIN_DURATION_MS = 1L
private const val ZERO = 0
private const val RESTORE_DELAY_STEP_0 = 0L
private const val RESTORE_DELAY_STEP_1 = 50L
private const val RESTORE_DELAY_STEP_2 = 150L
private const val RESTORE_DELAY_STEP_3 = 350L
private const val RESTORE_DELAY_STEP_4 = 700L
private const val RESTORE_DELAY_STEP_5 = 1200L
private const val RESTORE_DELAY_STEP_6 = 2000L
private val RESTORE_DELAYS_MS = longArrayOf(
    RESTORE_DELAY_STEP_0,
    RESTORE_DELAY_STEP_1,
    RESTORE_DELAY_STEP_2,
    RESTORE_DELAY_STEP_3,
    RESTORE_DELAY_STEP_4,
    RESTORE_DELAY_STEP_5,
    RESTORE_DELAY_STEP_6,
)
// endregion

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
    context.assets
        .open(path)
        .use { input -> BufferedReader(InputStreamReader(input)).readText() }
} catch (_: Throwable) {
    ""
}

internal fun WebView.configureBaseSettings(backgroundColor: String?) {
    alpha = 0f
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
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
    val viewConfiguration = android.view.ViewConfiguration.get(context)
    val slop = viewConfiguration.scaledTouchSlop
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
                val withinTime = dt in TAP_MIN_DURATION_MS..TAP_MAX_DURATION_MS
                val moved = (dx * dx + dy * dy) <= (slop * slop)
                if (withinTime && moved) {
                    onTap()
                }
            }
        }
        false
    }
}

internal fun WebView.setupScrollHandler(
    enabled: () -> Boolean,
    onScroll: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onAnchor: (String) -> Unit,
) {
    setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
        if (scrollX != ZERO) {
            v.scrollTo(0, scrollY)
        }
        if (enabled()) {
            onScroll(scrollY)
        }
    }
}

internal fun WebView.postRestore(target: Int, onDone: () -> Unit) {
    fun step(i: Int) {
        scrollTo(0, target)
        val closeNow = kotlin.math.abs(scrollY - target) <= RESTORE_CLOSE_DISTANCE_PX
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:step i=$i y=$scrollY target=$target close=$closeNow" }
        if (closeNow && i > 0) {
            onDone()
            return
        }
        if (i >= RESTORE_DELAYS_MS.lastIndex) {
            onDone()
            return
        }
        postDelayed({
            step(i + 1)
        }, RESTORE_DELAYS_MS[i + 1])
    }
    post {
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:start target=$target" }
        step(0)
    }
}

internal class ArticleClient(
    private val isInlineContent: Boolean,
    private val cssRef: Array<String?>,
    private val injectedCss: String?,
    private val initialScrollY: Int,
    private val fontScale: Float?,
    private val lineHeight: Float?,
    private val backgroundColor: String?,
    private val onFirstReady: () -> Unit,
    private val firstLoad: () -> Boolean,
    private val setReadyHidden: () -> Unit,
    private val startRestore: (Int) -> Unit,
    private val finishRestore: () -> Unit,
    private val setLastValues: (Float?, Float?) -> Unit,
    private val assets: WebViewAssetScripts,
    private val evaluate: (String) -> Unit,
) : WebViewClient() {
    // We must not call WebView.getUrl() off the main thread (shouldInterceptRequest runs on IO threads).
    // Capture the last known main-frame URL during main-thread callbacks.
    @Volatile private var lastMainFrameUrl: String? = null

    @Volatile private var injectedOnceForLoad: Boolean = false

    @Volatile private var lastAppliedFontScale: Float? = null

    @Volatile private var lastAppliedLineHeight: Float? = null

    @Volatile private var lastAppliedBg: String? = null

    private fun performInitialInjectionIfNeeded(isInline: Boolean, css: String?) {
        if (injectedOnceForLoad) {
            return
        }
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX inject:start inline=$isInline cssPresent=${!css.isNullOrBlank()}" }
        if (!isInline) {
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
            Logger.d(LOG_TAG) { "$SCROLL_PREFIX inject:themeCss applied size=${css.length}" }
        }
        injectedOnceForLoad = true
        // force re-application of reader vars after initial injection
        lastAppliedFontScale = null
        lastAppliedLineHeight = null
        lastAppliedBg = null
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX inject:done" }
    }

    private fun applyReaderVarsIfChanged(fs: Float?, lh: Float?, bgColor: String?) {
        val bg = bgColor.orEmpty()
        if (lastAppliedFontScale == fs && lastAppliedLineHeight == lh && lastAppliedBg == bg) {
            return
        }
        val fsArg = numArg(fs)
        val lhArg = numArg(lh)
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX applyVars fs=$fs lh=$lh bg='${bgColor ?: ""}'" }
        evaluate("lwbApplyReaderVars($fsArg, $lhArg, '$bg')")
        lastAppliedFontScale = fs
        lastAppliedLineHeight = lh
        lastAppliedBg = bg
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            lastMainFrameUrl = url
        }
        injectedOnceForLoad = false
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX onPageStarted url=$url firstLoad=${firstLoad()}" }
        if (firstLoad()) {
            setReadyHidden()
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val raw = request?.url?.toString()
        if (raw == null) {
            return false
        }
        val allowExternal = !(isInlineContent && raw.startsWith(ASSET_SCHEME_PREFIX))
        val mainFrame = request.isForMainFrame
        return allowExternal && mainFrame && openExternal(view, raw)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val u = request?.url?.toString()
        return intercept(view, u) ?: super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null) {
            return
        }
        if (url != null) {
            lastMainFrameUrl = url
        }
        Logger.d(LOG_TAG) {
            "$SCROLL_PREFIX onPageFinished url=$url firstLoad=${firstLoad()} initScroll=$initialScrollY"
        }
        performInitialInjectionIfNeeded(isInlineContent, injectedCss)
        applyReaderVarsIfChanged(fontScale, lineHeight, backgroundColor)
        if (firstLoad()) {
            onFirstReady()
            val target = initialScrollY
            if (target > 0) {
                try {
                    Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:schedule target=$target" }
                    startRestore(target)
                    view.postRestore(target) {
                        Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:done target=$target final=${view.scrollY}" }
                        finishRestore()
                    }
                } catch (_: Throwable) {
                    Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:error target=$target" }
                }
            } else {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX restore:skip target=$target" }
            }
        }
        setLastValues(fontScale, lineHeight)
        if (isInlineContent) {
            evaluate(assets.clampJs)
        }
    }

    private fun intercept(view: WebView?, url: String?): WebResourceResponse? {
        if (url == null) {
            return null
        }
        val theme = resolveThemeCssResponse(url)
        val asset = if (theme == null) {
            resolveInlineAssetResponse(view, url)
        } else {
            null
        }
        return theme ?: asset
    }

    private fun resolveThemeCssResponse(u: String): WebResourceResponse? {
        return try {
            val current = lastMainFrameUrl.orEmpty()
            if (current.isBlank()) {
                return null
            }
            val cur = Uri.parse(current)
            val req = Uri.parse(u)
            val isThemeReq = req.path?.endsWith(THEME_CSS_PATH_SUFFIX) == true
            val sameHost = cur.host == req.host && cur.scheme == req.scheme
            if (isThemeReq && sameHost) {
                val bytes = (cssRef[0] ?: "").toByteArray(Charsets.UTF_8)
                WebResourceResponse("text/css", "utf-8", java.io.ByteArrayInputStream(bytes))
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveInlineAssetResponse(view: WebView?, u: String): WebResourceResponse? {
        if (!(isInlineContent && u.startsWith(ASSET_SCHEME_PREFIX))) {
            return null
        }
        val path = u.removePrefix(ASSET_SCHEME_PREFIX)
        return try {
            val mime = when {
                path.endsWith(".js") -> {
                    "application/javascript"
                }
                path.endsWith(".css") -> {
                    "text/css"
                }
                else -> {
                    "application/octet-stream"
                }
            }
            val stream = view?.context?.assets?.open(path)
            if (stream == null) {
                null
            } else {
                WebResourceResponse(mime, "utf-8", stream)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun openExternal(view: WebView?, u: String): Boolean {
        return try {
            val ctx = view?.context ?: return false
            val intent = if (u.startsWith("intent://")) {
                android.content.Intent.parseUri(u, android.content.Intent.URI_INTENT_SCHEME)
            } else {
                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u))
            }
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
