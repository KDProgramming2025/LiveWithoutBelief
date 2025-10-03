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
import androidx.compose.runtime.MutableState
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
    // No longer used for complex fallback logic; kept minimal to disable scroll callbacks while restoring.
    val restoreActive: MutableState<Boolean>,
)

@Composable
private fun rememberArticleWebState(url: String?, htmlBody: String?): ArticleWebState {
    val ready = remember(url, htmlBody) { mutableStateOf(false) }
    val firstLoad = remember(url, htmlBody) { mutableStateOf(true) }
    val lastFontScale = remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    val lastLineHeight = remember(url, htmlBody) { mutableStateOf<Float?>(null) }
    val restoreActive = remember(url, htmlBody) { mutableStateOf(false) }
    return ArticleWebState(ready, firstLoad, lastFontScale, lastLineHeight, restoreActive)
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
                initialAnchor = initialAnchor,
                initialScrollY = initialScrollY,
                onTap = onTap,
                onScrollChanged = onScrollChanged,
                onAnchorChanged = onAnchorChanged,
            )
        },
        update = { webView ->
            // Fallback late one-time restore: if the persisted scroll value arrived AFTER the WebView was created
            // (common because we fetch it asynchronously) and the first load already finished without anchor restore,
            // perform a single restore now. We avoid interfering while an active restore is running or if already done.
            // Break the complex predicate into smaller nested checks to comply with detekt's ComplexCondition rule.
            if (!state.initialScrollApplied.value) {
                // Trigger only AFTER first load completes (firstLoad becomes false in setReady(true,false)).
                if (!state.restoreActive.value && state.ready.value && !state.firstLoad.value) {
                    val scrollTarget = initialScrollY ?: 0
                    if (scrollTarget > 0 && initialAnchor.isNullOrBlank()) {
                        state.initialScrollApplied.value = true
                        state.restoreActive.value = true
                        webView.postRestore(scrollTarget) {
                            state.restoreActive.value = false
                        }
                    }
                }
            }
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
            state.restoreActive.value = true
            // Mark applied so fallback late restore does not run.
            state.initialScrollApplied.value = true
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
        loadInlineArticle(webView, htmlBody, injectedCss, baseUrl)
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
    val finalHtml = buildInlineHtml(htmlBody, injectedCss)
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
    applyCssRef(webView, injectedCss)
    injectDomHelpers(webView)
    ensureBaseRuntime(webView)
    injectThemeJs(webView)
    applyInjectedCss(webView, injectedCss)
    applyReaderVars(webView, fontScale, lineHeight, backgroundColor)
    setLastValues(fontScale, lineHeight)
    reloadIfDifferentUrl(webView, url)
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
    val bg = backgroundColor ?: ""
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
    initialAnchor: String?,
    initialScrollY: Int?,
    onTap: (() -> Unit)?,
    onScrollChanged: ((Int) -> Unit)?,
    onAnchorChanged: ((String) -> Unit)?,
    readyState: () -> Pair<Boolean, Boolean>,
    setReady: (ready: Boolean, firstLoad: Boolean) -> Unit,
    setLastValues: (Float?, Float?) -> Unit,
    startRestore: (Int) -> Unit,
    restoreActiveProvider: () -> Boolean,
): WebView {
    val (ready, firstLoad) = readyState()
    val webView = WebView(ctx)
    val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
    // Store metadata instead of raw array to avoid unchecked casts.
    val meta = WebViewMeta(injectedCssRef = injectedCss, lastRequestedUrl = null)
    webView.tag = meta
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
        cssRef = arrayOf(meta.injectedCssRef, meta.lastRequestedUrl),
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
            }
        },
        setLastValues = setLastValues,
        assets = assets,
        evaluate = { js -> webView.evaluateJavascript(js, null) },
    )
    if (!url.isNullOrBlank()) {
        (webView.tag as? WebViewMeta)?.lastRequestedUrl = url
        webView.loadUrl(url)
    } else {
        val finalHtml = buildInlineHtml(htmlBody, injectedCss)
        webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
    }
    return webView
}
