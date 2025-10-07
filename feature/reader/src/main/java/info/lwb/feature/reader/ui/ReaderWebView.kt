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
// Removed scroll persistence + related coroutine scope usage.
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import info.lwb.core.common.log.Logger
import info.lwb.feature.reader.ui.internal.ArticleClient
import info.lwb.feature.reader.ui.internal.WebViewAssetScripts
import info.lwb.feature.reader.ui.internal.buildInlineHtml
import info.lwb.feature.reader.ui.internal.configureBaseSettings
import info.lwb.feature.reader.ui.internal.numArg
import info.lwb.feature.reader.ui.internal.safeLoadAsset
import info.lwb.feature.reader.ui.internal.setupScrollHandler
import info.lwb.feature.reader.ui.internal.setupTouchHandlers

// region logging
private const val SCROLL_PREFIX = "Scroll:"
private const val LOG_TAG = "ReaderWeb"
private const val ZERO_SCROLL = 0
private const val INLINE_PREFIX = "inline:"
private const val NO_HTML_FALLBACK = "nohtml"
private const val RADIX_HEX = 16
private const val UNKNOWN_URL_LENGTH = -1
// endregion

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
@Suppress("UnusedParameter")
internal fun ArticleWebView(
    htmlBody: String? = null,
    baseUrl: String? = null,
    url: String? = null,
    injectedCss: String? = null,
    fontScale: Float? = null,
    lineHeight: Float? = null,
    backgroundColor: String? = null,
    initialScrollY: Int? = null,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onScrollChanged: ((scrollY: Int) -> Unit)? = null,
    onAnchorChanged: ((anchor: String) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
    onWebViewCreated: ((WebView) -> Unit)? = null,
    onParagraphLongPress: ((id: String, text: String) -> Unit)? = null,
) {
    ArticleWebViewContent(
        htmlBody = htmlBody,
        baseUrl = baseUrl,
        url = url,
        injectedCss = injectedCss,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        initialScrollY = initialScrollY,
        modifier = modifier,
        onTap = onTap,
        onScrollChanged = onScrollChanged,
        onAnchorChanged = onAnchorChanged,
        onReady = onReady,
        onWebViewCreated = onWebViewCreated,
        onParagraphLongPress = onParagraphLongPress,
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
    initialScrollY: Int?,
    modifier: Modifier,
    onTap: (() -> Unit)?,
    onScrollChanged: ((scrollY: Int) -> Unit)?,
    onAnchorChanged: ((anchor: String) -> Unit)?,
    onReady: (() -> Unit)?,
    onWebViewCreated: ((WebView) -> Unit)?,
    onParagraphLongPress: ((id: String, text: String) -> Unit)?,
) {
    val state = rememberArticleWebState(
        url = url,
        htmlBody = htmlBody,
    )
    // Fire onReady exactly once after first ready transition
    androidx.compose.runtime.LaunchedEffect(state.ready.value) {
        if (state.ready.value) {
            onReady?.invoke()
        }
    }
    articleWebContentBody(
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
        initialScrollY = initialScrollY,
        onWebViewCreated = onWebViewCreated,
        onParagraphLongPress = onParagraphLongPress,
    )
}

@Composable
private fun articleWebContentBody(
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
    initialScrollY: Int?,
    onWebViewCreated: ((WebView) -> Unit)?,
    onParagraphLongPress: ((id: String, text: String) -> Unit)?,
) {
    // Hold a reference to the underlying WebView once created.
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current.applicationContext
    // Derive a stable key for scroll restoration: prefer remote URL, else a hashed inline body identity.
    val scrollKey = remember(url, htmlBody) {
        url ?: run {
            val bodyHash = htmlBody?.hashCode()?.toUInt()?.toString(RADIX_HEX) ?: NO_HTML_FALLBACK
            INLINE_PREFIX + bodyHash
        }
    }
    val providedInit = initialScrollY ?: ZERO_SCROLL
    // Only use stored scroll if caller didn't explicitly provide a non-zero initialScrollY.
    val storedScroll = remember(scrollKey, providedInit) {
        if (providedInit > ZERO_SCROLL) {
            ZERO_SCROLL
        } else {
            ScrollFileStore.get(context, scrollKey)
        }
    }
    val effectiveInit = if (providedInit > ZERO_SCROLL) {
        providedInit
    } else {
        storedScroll
    }
    val shortKey = shortenKeyForLog(scrollKey)
    Logger.d(LOG_TAG) {
        "$SCROLL_PREFIX compose:init key=$shortKey saved=$storedScroll provided=$providedInit effective=$effectiveInit"
    }
    val urlDescriptor = url ?: htmlBody?.length?.toString() ?: UNKNOWN_URL_LENGTH.toString()
    Logger.d(LOG_TAG) { "$SCROLL_PREFIX compose:ArticleWebContentBody enter url=$urlDescriptor" }
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
                onScrollChanged?.invoke(scrollY)
            },
            initialScrollY = effectiveInit,
            onAnchorChanged = onAnchorChanged,
            onWebViewCreated = { w ->
                webViewRef.value = w
                onWebViewCreated?.invoke(w)
            },
            onParagraphLongPress = onParagraphLongPress,
        )
        if (!state.ready.value) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

internal class ArticleWebState(
    val ready: MutableState<Boolean>,
    val firstLoad: MutableState<Boolean>,
    val lastFontScale: MutableState<Float?>,
    val lastLineHeight: MutableState<Float?>,
    val restoreActive: MutableState<Boolean>,
)

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
    onWebViewCreated: ((WebView) -> Unit)? = null,
    onParagraphLongPress: ((id: String, text: String) -> Unit)? = null,
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
                initialScrollY = initialScrollY,
                onTap = onTap,
                onScrollChanged = onScrollChanged,
                onAnchorChanged = onAnchorChanged,
                onWebViewCreated = onWebViewCreated,
                onParagraphLongPress = onParagraphLongPress,
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
    onWebViewCreated: ((WebView) -> Unit)?,
    onParagraphLongPress: ((id: String, text: String) -> Unit)?,
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
    onWebViewCreated = onWebViewCreated,
    onParagraphLongPress = onParagraphLongPress,
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
    val lp = webView.safeLoadAsset("webview/paragraph_longpress.js")
    if (lp.isNotBlank()) {
        webView.evaluateJavascript(lp, null)
    }
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
    onWebViewCreated: ((WebView) -> Unit)?,
    onParagraphLongPress: ((id: String, text: String) -> Unit)?,
    readyState: () -> Pair<Boolean, Boolean>,
    setReady: (ready: Boolean, firstLoad: Boolean) -> Unit,
    setLastValues: (Float?, Float?) -> Unit,
    startRestore: (Int) -> Unit,
    finishRestore: () -> Unit,
    restoreActiveProvider: () -> Boolean,
): WebView {
    val (_, firstLoadFlag) = readyState()
    Logger.d(LOG_TAG) {
        "$SCROLL_PREFIX webview:create start url=$url inline=${!url.isNullOrBlank()} initScroll=$initialScrollY"
    }
    val webView = WebView(ctx)
    // base setup
    initializeWebViewBase(
        webView = webView,
        injectedCss = injectedCss,
        backgroundColor = backgroundColor,
        onTap = onTap,
        restoreActiveProvider = restoreActiveProvider,
        onScrollChanged = onScrollChanged,
        onAnchorChanged = onAnchorChanged,
    )
    // client
    val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
    val assets = loadAssetScripts(webView)
    installArticleClient(
        webView = webView,
        isInlineContent = isInlineContent,
        injectedCss = injectedCss,
        initialScrollY = initialScrollY ?: 0,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        firstLoad = { firstLoadFlag },
        setReady = setReady,
        setLastValues = setLastValues,
        startRestore = startRestore,
        finishRestore = finishRestore,
        assets = assets,
        onParagraphLongPress = onParagraphLongPress,
    )
    // content load
    onWebViewCreated?.invoke(webView)
    loadInitialContent(
        webView = webView,
        url = url,
        htmlBody = htmlBody,
        injectedCss = injectedCss,
        baseUrl = baseUrl,
    )
    return webView
}

private fun initializeWebViewBase(
    webView: WebView,
    injectedCss: String?,
    backgroundColor: String?,
    onTap: (() -> Unit)?,
    restoreActiveProvider: () -> Boolean,
    onScrollChanged: ((Int) -> Unit)?,
    onAnchorChanged: ((String) -> Unit)?,
) {
    webView.tag = WebViewMeta(injectedCssRef = injectedCss, lastRequestedUrl = null)
    webView.configureBaseSettings(backgroundColor = backgroundColor)
    webView.setupTouchHandlers(onTap = onTap)
    webView.setupScrollHandler(
        enabled = {
            val enabledFlag = !restoreActiveProvider()
            if (!enabledFlag) {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX ignoring scroll during restore" }
            }
            enabledFlag
        },
        onScroll = { y ->
            val key = (webView.tag as? WebViewMeta)?.lastRequestedUrl ?: "inline"
            val saved = ScrollFileStore.saveIfChanged(
                ctx = webView.context,
                key = key,
                y = y,
            )
            if (saved) {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX emit scrollY=$y (saved key=${shortenKeyForLog(key)})" }
            }
            onScrollChanged?.invoke(y)
        },
        onAnchor = { a ->
            Logger.d(LOG_TAG) { "$SCROLL_PREFIX anchor a=$a" }
            onAnchorChanged?.invoke(a)
        },
    )
}

private fun loadAssetScripts(webView: WebView): WebViewAssetScripts = WebViewAssetScripts(
    clampJs = webView.safeLoadAsset(path = "webview/inject_clamp.js"),
    themeJs = webView.safeLoadAsset(path = "webview/inject_theme.js"),
    domHelpersJs = webView.safeLoadAsset(path = "webview/dom_helpers.js"),
)

private fun installArticleClient(
    webView: WebView,
    isInlineContent: Boolean,
    injectedCss: String?,
    initialScrollY: Int,
    fontScale: Float?,
    lineHeight: Float?,
    backgroundColor: String?,
    firstLoad: () -> Boolean,
    setReady: (Boolean, Boolean) -> Unit,
    setLastValues: (Float?, Float?) -> Unit,
    startRestore: (Int) -> Unit,
    finishRestore: () -> Unit,
    assets: WebViewAssetScripts,
    onParagraphLongPress: ((id: String, text: String) -> Unit)?,
) {
    if (onParagraphLongPress != null) {
        try {
            webView.addJavascriptInterface(ParagraphJsBridge(onParagraphLongPress), "LwbBridge")
        } catch (_: Throwable) {
            // ignore
        }
    }
    webView.webViewClient = ArticleClient(
        isInlineContent = isInlineContent,
        cssRef = arrayOf(
            (webView.tag as? WebViewMeta)?.injectedCssRef,
            (webView.tag as? WebViewMeta)?.lastRequestedUrl,
        ),
        injectedCss = injectedCss,
        initialScrollY = initialScrollY,
        fontScale = fontScale,
        lineHeight = lineHeight,
        backgroundColor = backgroundColor,
        onFirstReady = {
            setReady(true, false)
            webView.alpha = 1f
        },
        firstLoad = firstLoad,
        setReadyHidden = {
            // Preserve original semantics: pass current firstLoad() flag as second param
            val first = firstLoad()
            setReady(false, first)
            webView.alpha = 0f
        },
        startRestore = { target ->
            if (target > 0) {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX start target=$target" }
                startRestore(target)
            } else {
                Logger.d(LOG_TAG) { "$SCROLL_PREFIX start skipped target=$target" }
            }
        },
        finishRestore = finishRestore,
        setLastValues = setLastValues,
        assets = assets,
        evaluate = { js -> webView.evaluateJavascript(js, null) },
    )
}

private fun loadInitialContent(
    webView: WebView,
    url: String?,
    htmlBody: String?,
    injectedCss: String?,
    baseUrl: String?,
) {
    if (!url.isNullOrBlank()) {
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX load remote url=$url" }
        (webView.tag as? WebViewMeta)?.lastRequestedUrl = url
        webView.loadUrl(url)
    } else {
        val finalHtml = buildInlineHtml(body = htmlBody, css = injectedCss)
        Logger.d(LOG_TAG) { "$SCROLL_PREFIX load inline len=${finalHtml.length}" }
        webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "utf-8", null)
    }
}
