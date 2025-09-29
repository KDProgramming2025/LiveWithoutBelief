/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import android.util.Base64
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import info.lwb.feature.reader.ui.internal.ArticleClient
import info.lwb.feature.reader.ui.internal.WebViewAssetScripts
import info.lwb.feature.reader.ui.internal.buildInlineHtml
import info.lwb.feature.reader.ui.internal.configureBaseSettings
import info.lwb.feature.reader.ui.internal.numArg
import info.lwb.feature.reader.ui.internal.postRestore
import info.lwb.feature.reader.ui.internal.safeLoadAsset
import info.lwb.feature.reader.ui.internal.setupScrollHandler
import info.lwb.feature.reader.ui.internal.setupTouchHandlers

/**
 * Reader WebView composable for displaying either a remote URL or inline HTML content produced from a
 * server-converted DOCX (HTML/CSS/JS) representation. It injects theme & typography variables at runtime
 * while avoiding any inline JavaScript/CSS source code inside Kotlin (only function invocations are built).
 *
 * Responsibilities:
 *  - Loads either [url] or provided [htmlBody] (with optional [injectedCss]).
 *  - Applies reader configuration: [fontScale], [lineHeight], [backgroundColor].
 *  - Restores scroll position by [initialScrollY] or [initialAnchor].
 *  - Emits scroll changes and current anchor via [onScrollChanged] / [onAnchorChanged].
 *  - Detects taps and invokes [onTap].
 */
@Composable
internal fun ArticleWebView(
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
    initialAnchor: String? = null,
    onAnchorChanged: ((anchor: String) -> Unit)? = null,
) {
    var ready by remember(url, htmlBody) { mutableStateOf(false) }
    var firstLoad by remember(url, htmlBody) { mutableStateOf(true) }
    var lastFontScale by remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    var lastLineHeight by remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    var restoreActive by remember(url, htmlBody) { mutableStateOf(false) }
    var restoreTarget by remember(url, htmlBody) { mutableStateOf<Int?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val webView = WebView(ctx)
                val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
                val cssRef = arrayOf(injectedCss) // mutable reference used by intercept
                webView.tag = cssRef
                webView.configureBaseSettings(backgroundColor)
                webView.setupTouchHandlers(onTap)
                webView.setupScrollHandler(
                    enabled = { !restoreActive },
                    onScroll = { onScrollChanged?.invoke(it) },
                    onAnchor = { onAnchorChanged?.invoke(it) },
                )
                val assets = WebViewAssetScripts(
                    clampJs = webView.safeLoadAsset("webview/inject_clamp.js"),
                    themeJs = webView.safeLoadAsset("webview/inject_theme.js"),
                    domHelpersJs = webView.safeLoadAsset("webview/dom_helpers.js"),
                )
                webView.webViewClient = ArticleClient(
                    isInlineContent = isInlineContent,
                    cssRef = cssRef,
                    injectedCss = injectedCss,
                    initialAnchor = initialAnchor,
                    initialScrollY = initialScrollY ?: 0,
                    fontScale = fontScale,
                    lineHeight = lineHeight,
                    backgroundColor = backgroundColor,
                    onFirstReady = {
                        ready = true
                        firstLoad = false
                        webView.alpha = 1f
                    },
                    firstLoad = { firstLoad },
                    setReadyHidden = {
                        ready = false
                        webView.alpha = 0f
                    },
                    startRestore = { target ->
                        if (target <= 0) return@ArticleClient
                        restoreTarget = target
                        restoreActive = true
                        webView.postRestore(target) {
                            restoreActive = false
                            restoreTarget = null
                        }
                    },
                    setLastValues = { fs, lh ->
                        lastFontScale = fs
                        lastLineHeight = lh
                    },
                    assets = assets,
                    evaluate = { js -> webView.evaluateJavascript(js, null) },
                )
                // Initial content load
                if (!url.isNullOrBlank()) {
                    webView.loadUrl(url)
                } else {
                    val finalHtml = buildInlineHtml(htmlBody, injectedCss)
                    webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
                }
                webView
            },
            update = { webView ->
                updateArticleWebView(
                    webView = webView,
                    url = url,
                    htmlBody = htmlBody,
                    injectedCss = injectedCss,
                    baseUrl = baseUrl,
                    fontScale = fontScale,
                    lineHeight = lineHeight,
                    backgroundColor = backgroundColor,
                    initialScrollY = initialScrollY,
                    ready = ready,
                    restoreActive = restoreActive,
                    restoreTarget = restoreTarget,
                    setRestoreActive = { restoreActive = it },
                    setRestoreTarget = { restoreTarget = it },
                    setLastValues = { fs, lh ->
                        lastFontScale = fs
                        lastLineHeight = lh
                    },
                )
            },
        )
        if (!ready) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
// The large helpers & client implementation were extracted into separate internal files to
// reduce indentation depth and improve readability / maintainability.

private fun updateArticleWebView(
    webView: WebView,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    initialScrollY: Int?,
    ready: Boolean,
    restoreActive: Boolean,
    restoreTarget: Int?,
    setRestoreActive: (Boolean) -> Unit,
    setRestoreTarget: (Int?) -> Unit,
    setLastValues: (Float?, Float?) -> Unit,
) {
    if (!url.isNullOrBlank()) {
        (webView.tag as? Array<String?>)?.let { it[0] = injectedCss }
        val domHelpers = webView.safeLoadAsset("webview/dom_helpers.js")
        if (domHelpers.isNotBlank()) {
            webView.evaluateJavascript(domHelpers, null)
        }
        webView.evaluateJavascript("lwbEnsureLightMeta()", null)
        webView.evaluateJavascript("lwbRefreshThemeLink()", null)
        webView.evaluateJavascript("lwbEnsureBgOverride()", null)
        webView.evaluateJavascript("lwbDisableColorSchemeDarkening()", null)
        val themeJsCode = webView.safeLoadAsset("webview/inject_theme.js")
        if (themeJsCode.isNotBlank()) {
            webView.evaluateJavascript(themeJsCode, null)
        }
        injectedCss?.let { css ->
            if (css.isNotBlank()) {
                val b64 = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                webView.evaluateJavascript("window.lwbApplyThemeCss('$b64')", null)
            }
        }
        val fsArg = numArg(fontScale)
        val lhArg = numArg(lineHeight)
        val bg = backgroundColor ?: ""
        webView.evaluateJavascript("lwbApplyReaderVars($fsArg, $lhArg, '$bg')", null)
        setLastValues(fontScale, lineHeight)
        val desired = initialScrollY ?: 0
        if (ready && desired > 0) {
            val current = webView.scrollY
            val isNewTarget = restoreTarget != desired
            val far = kotlin.math.abs(current - desired) > 12
            if (isNewTarget || (!restoreActive && far)) {
                setRestoreTarget(desired)
                setRestoreActive(true)
                webView.postRestore(desired) {
                    setRestoreActive(false)
                    setRestoreTarget(null)
                }
            }
        }
        if (webView.url != url) {
            webView.loadUrl(url)
        }
    } else {
        val finalHtml = buildInlineHtml(htmlBody, injectedCss)
        webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
    }
}
