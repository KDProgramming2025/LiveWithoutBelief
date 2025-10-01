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
import androidx.compose.runtime.MutableState
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
    ArticleWebViewContent(
        htmlBody = htmlBody,
        baseUrl = baseUrl,
        url = url,
        injectedCss = injectedCss,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        modifier = modifier,
        onTap = onTap,
        onScrollChanged = onScrollChanged,
        initialScrollY = initialScrollY,
        initialAnchor = initialAnchor,
        onAnchorChanged = onAnchorChanged,
    )
}

@Composable
private fun ArticleWebViewContent(
    htmlBody: String?,
    baseUrl: String?,
    url: String?,
    injectedCss: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    modifier: Modifier,
    onTap: (() -> Unit)?,
    onScrollChanged: ((scrollY: Int) -> Unit)?,
    initialScrollY: Int?,
    initialAnchor: String?,
    onAnchorChanged: ((anchor: String) -> Unit)?,
) {
    val state = rememberArticleWebState(url, htmlBody)
    Box(modifier = modifier) {
        ArticleWebAndroidView(
            state = state,
            htmlBody = htmlBody,
            baseUrl = baseUrl,
            url = url,
            injectedCss = injectedCss,
            fontScale = fontScale,
            lineHeight = lineHeight,
            backgroundColor = backgroundColor,
            onTap = onTap,
            onScrollChanged = onScrollChanged,
            initialScrollY = initialScrollY,
            initialAnchor = initialAnchor,
            onAnchorChanged = onAnchorChanged,
        )
        if (!state.ready.value) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private class ArticleWebState(
    val ready: MutableState<Boolean>,
    val firstLoad: MutableState<Boolean>,
    val lastFontScale: MutableState<Float?>,
    val lastLineHeight: MutableState<Float?>,
    val restoreActive: MutableState<Boolean>,
    val restoreTarget: MutableState<Int?>,
)

@Composable
private fun rememberArticleWebState(url: String?, htmlBody: String?): ArticleWebState {
    val ready = remember(url, htmlBody) { mutableStateOf(false) }
    val firstLoad = remember(url, htmlBody) { mutableStateOf(true) }
    val lastFontScale = remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    val lastLineHeight = remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    val restoreActive = remember(url, htmlBody) { mutableStateOf(false) }
    val restoreTarget = remember(url, htmlBody) { mutableStateOf<Int?>(null) }
    return ArticleWebState(ready, firstLoad, lastFontScale, lastLineHeight, restoreActive, restoreTarget)
}

@Composable
private fun ArticleWebAndroidView(
    state: ArticleWebState,
    htmlBody: String?,
    baseUrl: String?,
    url: String?,
    injectedCss: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    onTap: (() -> Unit)?,
    onScrollChanged: ((scrollY: Int) -> Unit)?,
    initialScrollY: Int?,
    initialAnchor: String?,
    onAnchorChanged: ((anchor: String) -> Unit)?,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            buildWebView(
                ctx = ctx,
                state = state,
                htmlBody = htmlBody,
                baseUrl = baseUrl,
                url = url,
                injectedCss = injectedCss,
                fontScale = fontScale,
                lineHeight = lineHeight,
                backgroundColor = backgroundColor,
                initialAnchor = initialAnchor,
                initialScrollY = initialScrollY,
                onTap = onTap,
                onScrollChanged = onScrollChanged,
                onAnchorChanged = onAnchorChanged,
            )
        },
        update = { webView ->
            updateConfiguredWebView(
                webView = webView,
                state = state,
                url = url,
                htmlBody = htmlBody,
                injectedCss = injectedCss,
                baseUrl = baseUrl,
                fontScale = fontScale,
                lineHeight = lineHeight,
                backgroundColor = backgroundColor,
                initialScrollY = initialScrollY,
            )
        },
    )
}

private fun buildWebView(
    ctx: android.content.Context,
    state: ArticleWebState,
    htmlBody: String?,
    baseUrl: String?,
    url: String?,
    injectedCss: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    initialAnchor: String?,
    initialScrollY: Int?,
    onTap: (() -> Unit)?,
    onScrollChanged: ((Int) -> Unit)?,
    onAnchorChanged: ((String) -> Unit)?,
): WebView = createConfiguredWebView(
    ctx = ctx,
    url = url,
    htmlBody = htmlBody,
    injectedCss = injectedCss,
    baseUrl = baseUrl,
    backgroundColor = backgroundColor,
    fontScale = fontScale,
    lineHeight = lineHeight,
    initialAnchor = initialAnchor,
    initialScrollY = initialScrollY,
    onTap = onTap,
    onScrollChanged = onScrollChanged,
    onAnchorChanged = onAnchorChanged,
    readyState = { state.ready.value to state.firstLoad.value },
    setReady = { isReady, isFirst ->
        state.ready.value = isReady
        state.firstLoad.value = isFirst
    },
    setLastValues = { fs, lh ->
        state.lastFontScale.value = fs
        state.lastLineHeight.value = lh
    },
    startRestore = { target ->
        if (target > 0) {
            state.restoreTarget.value = target
            state.restoreActive.value = true
        }
    },
    finishRestore = {
        state.restoreActive.value = false
        state.restoreTarget.value = null
    },
    restoreActiveProvider = { state.restoreActive.value },
)

private fun updateConfiguredWebView(
    webView: WebView,
    state: ArticleWebState,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    initialScrollY: Int?,
) = updateArticleWebView(
    webView = webView,
    url = url,
    htmlBody = htmlBody,
    injectedCss = injectedCss,
    baseUrl = baseUrl,
    fontScale = fontScale,
    lineHeight = lineHeight,
    backgroundColor = backgroundColor,
    initialScrollY = initialScrollY,
    ready = state.ready.value,
    restoreActive = state.restoreActive.value,
    restoreTarget = state.restoreTarget.value,
    setRestoreActive = { state.restoreActive.value = it },
    setRestoreTarget = { state.restoreTarget.value = it },
    setLastValues = { fs, lh ->
        state.lastFontScale.value = fs
        state.lastLineHeight.value = lh
    },
)
// The large helpers & client implementation were extracted into separate internal files to
// reduce indentation depth and improve readability / maintainability.

private const val SCROLL_RESTORE_DISTANCE_THRESHOLD = 12

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
    if (url.isNullOrBlank()) {
        loadInlineArticle(webView, htmlBody, injectedCss, baseUrl)
        return
    }
    updateRemoteArticleWebView(
        webView = webView,
        url = url,
        injectedCss = injectedCss,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        initialScrollY = initialScrollY,
        ready = ready,
        restoreActive = restoreActive,
        restoreTarget = restoreTarget,
        setRestoreActive = setRestoreActive,
        setRestoreTarget = setRestoreTarget,
        setLastValues = setLastValues,
    )
}

private fun loadInlineArticle(
    webView: WebView,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
) {
    val finalHtml = buildInlineHtml(htmlBody, injectedCss)
    webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
}

private fun updateRemoteArticleWebView(
    webView: WebView,
    url: String,
    injectedCss: String?,
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
    applyCssRef(webView, injectedCss)
    injectDomHelpers(webView)
    ensureBaseRuntime(webView)
    injectThemeJs(webView)
    applyInjectedCss(webView, injectedCss)
    applyReaderVars(webView, fontScale, lineHeight, backgroundColor)
    setLastValues(fontScale, lineHeight)
    maybeRestoreScroll(
        webView = webView,
        initialScrollY = initialScrollY,
        ready = ready,
        restoreActive = restoreActive,
        restoreTarget = restoreTarget,
        setRestoreActive = setRestoreActive,
        setRestoreTarget = setRestoreTarget,
    )
    reloadIfDifferentUrl(webView, url)
}

private fun applyCssRef(webView: WebView, injectedCss: String?) {
    (webView.tag as? Array<String?>)?.let { it[0] = injectedCss }
}

private fun injectDomHelpers(webView: WebView) {
    val domHelpers = webView.safeLoadAsset("webview/dom_helpers.js")
    if (domHelpers.isNotBlank()) {
        webView.evaluateJavascript(domHelpers, null)
    }
}

private fun ensureBaseRuntime(webView: WebView) {
    webView.evaluateJavascript("lwbEnsureLightMeta()", null)
    webView.evaluateJavascript("lwbRefreshThemeLink()", null)
    webView.evaluateJavascript("lwbEnsureBgOverride()", null)
    webView.evaluateJavascript("lwbDisableColorSchemeDarkening()", null)
}

private fun injectThemeJs(webView: WebView) {
    val themeJsCode = webView.safeLoadAsset("webview/inject_theme.js")
    if (themeJsCode.isNotBlank()) {
        webView.evaluateJavascript(themeJsCode, null)
    }
}

private fun applyInjectedCss(webView: WebView, injectedCss: String?) {
    injectedCss?.let { css ->
        if (css.isNotBlank()) {
            val b64 = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            webView.evaluateJavascript("window.lwbApplyThemeCss('$b64')", null)
        }
    }
}

private fun applyReaderVars(
    webView: WebView,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
) {
    val fsArg = numArg(fontScale)
    val lhArg = numArg(lineHeight)
    val bg = backgroundColor ?: ""
    webView.evaluateJavascript("lwbApplyReaderVars($fsArg, $lhArg, '$bg')", null)
}

private fun maybeRestoreScroll(
    webView: WebView,
    initialScrollY: Int?,
    ready: Boolean,
    restoreActive: Boolean,
    restoreTarget: Int?,
    setRestoreActive: (Boolean) -> Unit,
    setRestoreTarget: (Int?) -> Unit,
) {
    val desired = initialScrollY ?: 0
    if (!ready || desired <= 0) {
        return
    }
    val current = webView.scrollY
    val isNewTarget = restoreTarget != desired
    val far = kotlin.math.abs(current - desired) > SCROLL_RESTORE_DISTANCE_THRESHOLD
    if (isNewTarget || (!restoreActive && far)) {
        setRestoreTarget(desired)
        setRestoreActive(true)
        webView.postRestore(desired) {
            setRestoreActive(false)
            setRestoreTarget(null)
        }
    }
}

private fun reloadIfDifferentUrl(webView: WebView, url: String) {
    if (webView.url != url) {
        webView.loadUrl(url)
    }
}

private fun createConfiguredWebView(
    ctx: android.content.Context,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
    backgroundColor: String?,
    fontScale: Float?,
    lineHeight: Float?,
    initialAnchor: String?,
    initialScrollY: Int?,
    onTap: (() -> Unit)?,
    onScrollChanged: ((Int) -> Unit)?,
    onAnchorChanged: ((String) -> Unit)?,
    readyState: () -> Pair<Boolean, Boolean>,
    setReady: (ready: Boolean, firstLoad: Boolean) -> Unit,
    setLastValues: (Float?, Float?) -> Unit,
    startRestore: (Int) -> Unit,
    finishRestore: () -> Unit,
    restoreActiveProvider: () -> Boolean,
): WebView {
    val (ready, firstLoad) = readyState()
    val webView = WebView(ctx)
    val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
    val cssRef = arrayOf(injectedCss)
    webView.tag = cssRef
    webView.configureBaseSettings(backgroundColor)
    webView.setupTouchHandlers(onTap)
    webView.setupScrollHandler(
        enabled = { !restoreActiveProvider() },
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
            setReady(true, false)
            webView.alpha = 1f
        },
        firstLoad = { firstLoad },
        setReadyHidden = {
            setReady(false, firstLoad)
            webView.alpha = 0f
        },
        startRestore = { target ->
            if (target > 0) {
                startRestore(target)
                webView.postRestore(target) {
                    finishRestore()
                }
            }
        },
        setLastValues = setLastValues,
        assets = assets,
        evaluate = { js -> webView.evaluateJavascript(js, null) },
    )
    if (!url.isNullOrBlank()) {
        webView.loadUrl(url)
    } else {
        val finalHtml = buildInlineHtml(htmlBody, injectedCss)
        webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
    }
    return webView
}
