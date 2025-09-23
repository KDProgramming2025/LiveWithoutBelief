/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.AddAnnotationUseCase
import info.lwb.feature.annotations.DiscussionThreadSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

// Data holder for reader settings provided by caller (ViewModel layer wires flows & mutations)
data class ReaderSettingsState(
    val fontScale: Double,
    val lineHeight: Double,
    val onFontScaleChange: (Double) -> Unit,
    val onLineHeightChange: (Double) -> Unit,
)

@Composable
fun ArticleWebView(
    htmlBody: String? = null,
    baseUrl: String? = null,
    url: String? = null,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                // Storage & caching
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                // Layout/scroll behavior
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
                    if (scrollX != 0) v.scrollTo(0, scrollY)
                }
                // Viewport and width-fit
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Default caching
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                // Load clamp script from assets to adjust viewport, CSS and responsive iframes
                fun loadClampJs(): String {
                    val am = context.assets
                    am.open("webview/inject_clamp.js").use { input ->
                        return BufferedReader(InputStreamReader(input)).readText()
                    }
                }
                val clampJs by lazy { loadClampJs() }
                val isInlineContent = url.isNullOrBlank() && !htmlBody.isNullOrBlank()
                // Disable long-click to avoid "Save video" or "Save audio" menus
                isLongClickable = false
                setOnLongClickListener { true }
                setDownloadListener { _, _, _, _, _ ->
                    // Block default download handling inside WebView
                }
                // Detect simple single-tap gestures without interfering with scroll/long-press
                var downX = 0f
                var downY = 0f
                var downTs = 0L
                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                setOnTouchListener { _, ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downX = ev.x; downY = ev.y; downTs = ev.eventTime
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dt = ev.eventTime - downTs
                            val dx = ev.x - downX
                            val dy = ev.y - downY
                            if (dt in 1..250 && (dx*dx + dy*dy) <= (slop * slop)) {
                                onTap?.invoke()
                            }
                        }
                    }
                    false // don't consume; let WebView handle it
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val u = url ?: return false
                        if (isInlineContent && u.startsWith("lwb-assets://")) return false
                        return openExternal(u)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val u = request?.url?.toString() ?: return false
                        if (isInlineContent && u.startsWith("lwb-assets://")) return false
                        if (request?.isForMainFrame == true) {
                            return openExternal(u)
                        }
                        return false
                    }
                    private fun openExternal(u: String): Boolean {
                        return try {
                            if (u.startsWith("intent://")) {
                                val intent = Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                                context.startActivity(intent)
                                true
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                context.startActivity(intent)
                                true
                            }
                        } catch (_: Throwable) {
                            false
                        }
                    }
                    private fun intercept(u: String?): WebResourceResponse? {
                        val url = u ?: return null
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
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        return null
                    }
                    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                        return intercept(url) ?: super.shouldInterceptRequest(view, url)
                    }
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): WebResourceResponse? {
                        val u = request?.url?.toString()
                        return intercept(u) ?: super.shouldInterceptRequest(view, request)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isInlineContent) {
                            evaluateJavascript(clampJs, null)
                        }
                    }
                }
                if (!url.isNullOrBlank()) {
                    loadUrl(url)
                } else {
                    loadDataWithBaseURL(
                        baseUrl,
                        htmlBody.orEmpty(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            }
        },
        update = { webView ->
            if (!url.isNullOrBlank()) {
                webView.loadUrl(url)
            } else {
                webView.loadDataWithBaseURL(baseUrl, htmlBody.orEmpty(), "text/html", "utf-8", null)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    articleTitle: String,
    htmlBody: String,
    settings: ReaderSettingsState,
    pages: List<Page>? = null,
    currentPageIndex: Int = 0,
    onPageChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // FAB visibility state with auto-hide timer
    var fabVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }
    fun showFabTemporarily() {
        fabVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5000)
            fabVisible = false
        }
    }
    LaunchedEffect(Unit) { showFabTemporarily() }
    DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }

    val blocks = remember(htmlBody) { parseHtmlToBlocks(htmlBody) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf(listOf<SearchHit>()) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(articleTitle) }) },
        bottomBar = {
            ReaderControlsBar(settings = settings) { font, line ->
                settings.onFontScaleChange(font)
                settings.onLineHeightChange(line)
            }
        },
        floatingActionButton = {
            if (fabVisible) {
                FloatingActionButton(onClick = { showFabTemporarily() }) {
                    Icon(Icons.Filled.Edit, contentDescription = "FAB")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .pointerInput(Unit) {
                    // Show FAB on single taps only; ignore long press/drag/scroll
                    detectTapGestures(
                        onTap = { showFabTemporarily() }
                    )
                }
        ) {
            SearchBar(
                searchQuery,
                occurrences = searchHits.size,
                currentIndex = if (searchHits.isEmpty()) 0 else currentSearchIndex + 1,
                onPrev = {
                    if (searchHits.isNotEmpty()) {
                        currentSearchIndex =
                            (currentSearchIndex - 1 + searchHits.size) % searchHits.size
                    }
                },
                onNext = {
                    if (searchHits.isNotEmpty()) {
                        currentSearchIndex = (currentSearchIndex + 1) % searchHits.size
                    }
                },
            ) { q ->
                searchQuery = q
            }
            // Recompute matches whenever query or blocks change (using full content for simplicity)
            LaunchedEffect(searchQuery, blocks) {
                searchHits = buildSearchHits(pages, blocks, searchQuery)
                currentSearchIndex = 0
            }
            val pageList = pages
            if (pageList != null && pageList.size > 1) {
                // Simple horizontal pager substitute using Row + manual buttons (avoid new dependency)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Page ${currentPageIndex + 1} / ${pageList.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row {
                        androidx.compose.material3.Button(
                            enabled = currentPageIndex > 0,
                            onClick = {
                                onPageChange((currentPageIndex - 1).coerceAtLeast(0))
                            },
                        ) { Text("Prev") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            enabled = currentPageIndex < pageList.lastIndex,
                            onClick = {
                                onPageChange((currentPageIndex + 1).coerceAtMost(pageList.lastIndex))
                            },
                        ) { Text("Next") }
                    }
                }
                val pageBlocks =
                    remember(currentPageIndex, pageList) { pageList.getOrNull(currentPageIndex)?.blocks ?: emptyList() }
                val listState = rememberLazyListState()
                // Auto-scroll to current hit if it's on this page
                LaunchedEffect(currentSearchIndex, searchHits, currentPageIndex) {
                    val hit = searchHits.getOrNull(currentSearchIndex)
                    if (hit != null && hit.pageIndex == currentPageIndex) {
                        listState.animateScrollToItem(hit.blockIndex)
                    }
                }
                val contentModifier = if (isWide) Modifier.weight(1f) else Modifier.fillMaxSize()
                val toc: List<HeadingItem> = remember(pageList) { buildHeadingItems(pageList) }
                Row(Modifier.fillMaxSize()) {
                    if (isWide && toc.isNotEmpty()) {
                        Surface(
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight(),
                        ) {
                            LazyColumn(
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            ) {
                                items(toc.size) { i ->
                                    val h = toc[i]
                                    Text(
                                        text = h.text,
                                        style = when (h.level) {
                                            1 -> MaterialTheme.typography.titleMedium
                                            2 -> MaterialTheme.typography.titleSmall
                                            else -> MaterialTheme.typography.bodySmall
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPageChange(h.pageIndex) }
                                            .padding(vertical = 4.dp),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                    var openAnnotationFor by remember { mutableStateOf<String?>(null) }
                    val deps = hiltViewModel<ReaderDeps>()
                    LazyColumn(contentModifier, state = listState) {
                        items(pageBlocks.size) { idx ->
                            val b = pageBlocks[idx]
                            when (b) {
                                is ContentBlock.Paragraph -> Column(Modifier.fillMaxWidth()) {
                                    ParagraphBlock(
                                        text = b.text,
                                        query = searchQuery,
                                        settings = settings,
                                        activeRange = searchHits
                                            .getOrNull(currentSearchIndex)
                                            ?.takeIf { it.pageIndex == currentPageIndex && it.blockIndex == idx }
                                            ?.range,
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = {
                                            // Annotate whole paragraph for MVP
                                            val articleId = articleTitle
                                            val range = 0 to b.text.length
                                            // Fire and open sheet after id returned
                                            deps.scope.launch {
                                                val res = deps.addAnnotation(
                                                    articleId,
                                                    range.first,
                                                    range.second,
                                                    b.text.hashCode().toString(),
                                                )
                                                if (res is info.lwb.core.common.Result.Success) {
                                                    openAnnotationFor = res.data
                                                }
                                            }
                                        }) { Icon(Icons.Filled.Edit, contentDescription = "Annotate") }
                                    }
                                }
                                is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                                is ContentBlock.Image -> AsyncImage(
                                    model = b.url,
                                    contentDescription = b.alt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                )
                                is ContentBlock.Audio -> AudioBlock(b.url)
                                is ContentBlock.YouTube -> YouTubeBlock(b.videoId)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (openAnnotationFor != null) {
                        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { openAnnotationFor = null }) {
                            DiscussionThreadSheet(annotationId = openAnnotationFor!!)
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(currentSearchIndex, searchHits) {
                    val hit = searchHits.getOrNull(currentSearchIndex)
                    if (hit != null) listState.animateScrollToItem(hit.blockIndex)
                }
                var openAnnotationFor by remember { mutableStateOf<String?>(null) }
                val deps = hiltViewModel<ReaderDeps>()
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(blocks.size) { idx ->
                        val b = blocks[idx]
                        when (b) {
                            is ContentBlock.Paragraph -> Column(Modifier.fillMaxWidth()) {
                                ParagraphBlock(
                                    text = b.text,
                                    query = searchQuery,
                                    settings = settings,
                                    activeRange = searchHits
                                        .getOrNull(currentSearchIndex)
                                        ?.takeIf { it.blockIndex == idx }
                                        ?.range,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = {
                                        val articleId = articleTitle
                                        val range = 0 to b.text.length
                                        deps.scope.launch {
                                            val res = deps.addAnnotation(
                                                articleId,
                                                range.first,
                                                range.second,
                                                b.text.hashCode().toString(),
                                            )
                                            if (res is info.lwb.core.common.Result.Success) {
                                                openAnnotationFor = res.data
                                            }
                                        }
                                    }) { Icon(Icons.Filled.Edit, contentDescription = "Annotate") }
                                }
                            }
                            is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                            is ContentBlock.Image -> AsyncImage(
                                model = b.url,
                                contentDescription = b.alt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                            is ContentBlock.Audio -> AudioBlock(b.url)
                            is ContentBlock.YouTube -> YouTubeBlock(b.videoId)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (openAnnotationFor != null) {
                    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { openAnnotationFor = null }) {
                        DiscussionThreadSheet(annotationId = openAnnotationFor!!)
                    }
                }
            }
        }
    }
}

// Internal small helper to access use cases without refactoring the entire screen into a VM here.
@HiltViewModel
internal class ReaderDeps @Inject constructor(
    private val addAnnotationUseCase: AddAnnotationUseCase,
) : androidx.lifecycle.ViewModel() {
    val scope get() = viewModelScope
    suspend fun addAnnotation(articleId: String, start: Int, end: Int, hash: String) =
        addAnnotationUseCase(articleId, start, end, hash)
}

@Composable
private fun ParagraphBlock(text: String, query: String, settings: ReaderSettingsState, activeRange: IntRange? = null) {
    val matches =
        if (query.isBlank()) {
            emptyList()
        } else {
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                .findAll(text)
                .map { it.range }
                .toList()
        }
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { range ->
            if (range.first > lastIndex) append(text.substring(lastIndex, range.first))
            val highlightColor =
                if (activeRange == range) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                }
            withStyle(MaterialTheme.typography.bodyLarge.toSpanStyle().copy(background = highlightColor)) {
                append(text.substring(range))
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
    val baseStyle = MaterialTheme.typography.bodyLarge
    val scaledFont = (baseStyle.fontSize.value * settings.fontScale).coerceAtLeast(10.0).sp
    val scaledLineHeight = (baseStyle.lineHeight.value * settings.lineHeight).coerceAtLeast(12.0).sp
    Text(
        annotated,
        style = baseStyle.copy(fontSize = scaledFont, lineHeight = scaledLineHeight),
    )
}

@Composable
private fun HeadingBlock(level: Int, text: String) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(text, style = style)
}

@Composable
private fun AudioBlock(url: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        AudioPlayer(url)
    }
}

@Composable
private fun AudioPlayer(url: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.fromUri(url)
            setMediaItem(item)
            prepare()
            addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        ready =
                            playbackState != androidx.media3.common.Player.STATE_BUFFERING &&
                            playbackState != androidx.media3.common.Player.STATE_IDLE
                    }
                },
            )
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!ready) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.play()
                isPlaying = true
            }
        }) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = if (player.isPlaying) "Pause audio" else "Play audio",
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = url.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun YouTubeBlock(videoId: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    // Storage & caching
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    isLongClickable = false
                    setOnLongClickListener { true }
                    setDownloadListener { _, _, _, _, _ -> }
                    // Prevent sideways panning inside the embedded frame
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
                        if (scrollX != 0) v.scrollTo(0, scrollY)
                    }
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val u = url ?: return false
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                context.startActivity(intent)
                                true
                            } catch (_: Throwable) { false }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            if (request?.isForMainFrame == true) {
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                    context.startActivity(intent)
                                    true
                                } catch (_: Throwable) { false }
                            }
                            return false
                        }
                    }
                    // Avoid inline HTML; load the embed URL directly.
                    loadUrl("https://www.youtube.com/embed/$videoId")
                }
            },
            )
            Spacer(Modifier.height(8.dp))
            val context = LocalContext.current
            Button(
                onClick = {
                    val target = "https://www.youtube.com/watch?v=$videoId"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                        context.startActivity(intent)
                    } catch (_: Throwable) { }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) { Text("Watch this video on YouTube") }
        }
    }
}

@Composable
private fun ReaderControlsBar(settings: ReaderSettingsState, onChange: (Double, Double) -> Unit) {
    Surface(shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                "Font: ${"%.2f".format(settings.fontScale)}  Line: ${"%.2f".format(settings.lineHeight)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontScale.toFloat(),
                    onValueChange = { onChange(it.toDouble().coerceIn(0.8, 1.6), settings.lineHeight) },
                    valueRange = 0.8f..1.6f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = settings.lineHeight.toFloat(),
                    onValueChange = { onChange(settings.fontScale, it.toDouble().coerceIn(1.0, 2.0)) },
                    valueRange = 1.0f..2.0f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    occurrences: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search") },
        )
        if (occurrences > 0) {
            Text(
                "$currentIndex/$occurrences",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
            Button(onClick = onPrev, enabled = occurrences > 0) { Text("Prev") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onNext, enabled = occurrences > 0) { Text("Next") }
        }
    }
}

@Preview(name = "Reader Light", showBackground = true)
@Composable
private fun PreviewReaderLight() {
    val sampleHtml = """
        <h1>Sample Title</h1>
        <p>Paragraph one with some text for preview.</p>
        <p>Paragraph two with more content to demonstrate scaling.</p>
        <img src='https://example.com/x.png' alt='x'/>
    """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Article",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(1.0, 1.2, {}, {}),
    )
}

@Preview(name = "Reader Dark", showBackground = true)
@Composable
private fun PreviewReaderDark() {
    val sampleHtml = """
        <h1>Sample Title</h1>
        <p>Dark theme paragraph example.</p>
        <audio src='https://example.com/a.mp3'></audio>
    """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Dark",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(1.0, 1.2, {}, {}),
    )
}
