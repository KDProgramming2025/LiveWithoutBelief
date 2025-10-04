/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import android.webkit.WebView
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
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
import info.lwb.feature.reader.ui.internal.safeLoadAsset
import info.lwb.feature.reader.ui.internal.setupScrollHandler
import info.lwb.feature.reader.ui.internal.setupTouchHandlers

/**
 * Reader WebView composable for displaying either a remote URL or inline HTML content produced from a
 * server-converted DOCX (HTML/CSS/JS) representation. It injects theme & typography variables at runtime
 * while avoiding any inline JavaScript/CSS source code inside Kotlin.
 * (Only function invocations are built.)
 *
 * Responsibilities:
 *  - Loads either [url] or provided [htmlBody] (with optional [injectedCss]).
 *  - Applies reader configuration: [fontScale], [lineHeight], [backgroundColor].
 *  - Restores scroll position by numeric [initialScrollY] only (anchor logic removed).
 *  - Emits scroll changes and current anchor via [onScrollChanged] / [onAnchorChanged].
 *  - Detects taps and invokes [onTap].
 */
@Composable
internal fun ArticleWebView(
    articleId: String,
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
    onAnchorChanged: ((anchor: String) -> Unit)? = null, // retained param for backward compatibility (unused)
) {
    ArticleWebViewContent(
        articleId = articleId,
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
        onAnchorChanged = onAnchorChanged,
    )
}

@Composable
private fun ArticleWebViewContent(
    articleId: String,
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
    onAnchorChanged: ((anchor: String) -> Unit)?,
) {
    val scrollVmProvider =
        androidx.hilt.navigation.compose
            .hiltViewModel<info.lwb.feature.reader.ScrollViewModel>()
    val scrollVm: info.lwb.feature.reader.ScrollViewModel = scrollVmProvider
    val scope = rememberCoroutineScope()
    var initialScroll by remember(key1 = articleId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(key1 = articleId) {
        initialScroll = try {
            scrollVm.observe(articleId).first()
        } catch (_: Throwable) {
            0
        }
    }
    val state = rememberArticleWebState(
        url = url,
        htmlBody = htmlBody,
    )

    // Early return for loading state reduces cognitive complexity
    if (initialScroll == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    ArticleWebContentBody(
        modifier = modifier,
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
        onAnchorChanged = onAnchorChanged,
        initialScroll = initialScroll,
        articleId = articleId,
        scope = scope,
        scrollVm = scrollVm,
    )
}

@Composable
private fun ArticleWebContentBody(
    modifier: Modifier,
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
    onAnchorChanged: ((anchor: String) -> Unit)?,
    initialScroll: Int?,
    articleId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    scrollVm: info.lwb.feature.reader.ScrollViewModel,
) {
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
            onScrollChanged = { scrollY ->
                if (scrollY >= 0) {
                    scope.launch {
                        try {
                            scrollVm.save(articleId, scrollY)
                        } catch (_: Throwable) {
                            // ignore persistence errors
                        }
                    }
                }
                onScrollChanged?.invoke(scrollY)
            },
            initialScrollY = initialScroll,
            onAnchorChanged = onAnchorChanged,
        )
        if (!state.ready.value) {
            Box(modifier = Modifier.fillMaxSize()) {
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
)

@Composable
private fun rememberArticleWebState(url: String?, htmlBody: String?): ArticleWebState {
    val ready = remember(key1 = url, key2 = htmlBody) { mutableStateOf(false) }
    val firstLoad = remember(key1 = url, key2 = htmlBody) { mutableStateOf(true) }
    val lastFontScale = remember(key1 = url, key2 = htmlBody) { mutableStateOf<Float?>(null) }
    val lastLineHeight = remember(key1 = url, key2 = htmlBody) { mutableStateOf<Float?>(null) }
    val restoreActive = remember(key1 = url, key2 = htmlBody) { mutableStateOf(false) }
    return ArticleWebState(
        ready = ready,
        firstLoad = firstLoad,
        lastFontScale = lastFontScale,
        lastLineHeight = lastLineHeight,
        restoreActive = restoreActive,
    )
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
    onAnchorChanged: ((anchor: String) -> Unit)?,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize(),
        factory = { ctx ->
            createArticleWebView(
                ctx = ctx,
                state = state,
                htmlBody = htmlBody,
                baseUrl = baseUrl,
                url = url,
                injectedCss = injectedCss,
                fontScale = fontScale,
                lineHeight = lineHeight,
                backgroundColor = backgroundColor,
                initialScrollY = initialScrollY,
                onTap = onTap,
                onScrollChanged = onScrollChanged,
                onAnchorChanged = onAnchorChanged,
            )
        },
        update = { webView ->
            applyArticleWebViewUpdates(
                webView = webView,
                state = state,
                url = url,
                htmlBody = htmlBody,
                injectedCss = injectedCss,
                baseUrl = baseUrl,
                fontScale = fontScale,
                lineHeight = lineHeight,
                backgroundColor = backgroundColor,
            )
        },
    )
}

private fun createArticleWebView(
    ctx: android.content.Context,
    state: ArticleWebState,
    htmlBody: String?,
    baseUrl: String?,
    url: String?,
    injectedCss: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
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
            state.restoreActive.value = true
        }
    },
    finishRestore = {
        state.restoreActive.value = false
    },
    restoreActiveProvider = { state.restoreActive.value },
)

private fun applyArticleWebViewUpdates(
    webView: WebView,
    state: ArticleWebState,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
) = applyArticleContent(
    webView = webView,
    url = url,
    htmlBody = htmlBody,
    injectedCss = injectedCss,
    baseUrl = baseUrl,
    fontScale = fontScale,
    lineHeight = lineHeight,
    backgroundColor = backgroundColor,
    setLastValues = { fs, lh ->
        state.lastFontScale.value = fs
        state.lastLineHeight.value = lh
    },
)
// The large helpers & client implementation were extracted into separate internal files to
// reduce indentation depth and improve readability / maintainability.

private fun applyArticleContent(
    webView: WebView,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    setLastValues: (Float?, Float?) -> Unit,
) {
    if (url.isNullOrBlank()) {
        loadInlineArticle(
            webView = webView,
            htmlBody = htmlBody,
            injectedCss = injectedCss,
            baseUrl = baseUrl,
        )
        return
    }
    applyRemoteArticleContent(
        webView = webView,
        url = url,
        injectedCss = injectedCss,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        setLastValues = setLastValues,
    )
}

private fun loadInlineArticle(webView: WebView, htmlBody: String?, injectedCss: String?, baseUrl: String?) {
    val finalHtml = buildInlineHtml(body = htmlBody, css = injectedCss)
    webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
}

private fun applyRemoteArticleContent(
    webView: WebView,
    url: String,
    injectedCss: String?,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    setLastValues: (Float?, Float?) -> Unit,
) {
    applyCssRef(
        webView = webView,
        injectedCss = injectedCss,
    )
    injectDomHelpers(webView = webView)
    ensureBaseRuntime(webView = webView)
    injectThemeJs(webView = webView)
    applyInjectedCss(
        webView = webView,
        injectedCss = injectedCss,
    )
    applyReaderVars(
        webView = webView,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
    )
    setLastValues(fontScale, lineHeight)
    reloadIfDifferentUrl(
        webView = webView,
        url = url,
    )
}

// Metadata object used as WebView.tag to avoid unchecked array casts.
private data class WebViewMeta(var injectedCssRef: String?, var lastRequestedUrl: String?)

private fun applyCssRef(webView: WebView, injectedCss: String?) {
    (webView.tag as? WebViewMeta)?.injectedCssRef = injectedCss
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

private fun applyReaderVars(webView: WebView, fontScale: Float?, lineHeight: Float?, backgroundColor: String?) {
    val fsArg = numArg(fontScale)
    val lhArg = numArg(lineHeight)
    val bg = backgroundColor.orEmpty()
    webView.evaluateJavascript("lwbApplyReaderVars($fsArg, $lhArg, '$bg')", null)
}

// One-time scroll restoration now handled exclusively by ArticleClient via startRestore/finishRestore.

private fun reloadIfDifferentUrl(webView: WebView, url: String) {
    val meta = webView.tag as? WebViewMeta
    if (meta?.lastRequestedUrl == url) {
        return
    }
    if (webView.url == url) {
        meta?.lastRequestedUrl = url
        return
    }
    meta?.lastRequestedUrl = url
    webView.loadUrl(url)
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
    val (_, firstLoad) = readyState() // ignore current ready flag value
    val webView = WebView(ctx)
    val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
    val meta = WebViewMeta(injectedCssRef = injectedCss, lastRequestedUrl = null)
    webView.tag = meta
    webView.configureBaseSettings(backgroundColor = backgroundColor)
    webView.setupTouchHandlers(onTap = onTap)
    webView.setupScrollHandler(
        enabled = { !restoreActiveProvider() },
        onScroll = { y -> onScrollChanged?.invoke(y) },
        onAnchor = { a -> onAnchorChanged?.invoke(a) },
    )
    val assets = WebViewAssetScripts(
        clampJs = webView.safeLoadAsset(path = "webview/inject_clamp.js"),
        themeJs = webView.safeLoadAsset(path = "webview/inject_theme.js"),
        domHelpersJs = webView.safeLoadAsset(path = "webview/dom_helpers.js"),
    )
    webView.webViewClient = ArticleClient(
        isInlineContent = isInlineContent,
        cssRef = arrayOf(meta.injectedCssRef, meta.lastRequestedUrl),
        injectedCss = injectedCss,
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
            }
        },
        finishRestore = finishRestore,
        setLastValues = setLastValues,
        assets = assets,
        evaluate = { js -> webView.evaluateJavascript(js, null) },
    )
    if (!url.isNullOrBlank()) {
        (webView.tag as? WebViewMeta)?.lastRequestedUrl = url
        webView.loadUrl(url)
    } else {
        val finalHtml = buildInlineHtml(body = htmlBody, css = injectedCss)
        webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
    }
    return webView
}
