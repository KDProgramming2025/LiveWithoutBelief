/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

// cleaned after modularization
// cleaned after modularization
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.AddAnnotationUseCase
import info.lwb.feature.annotations.DiscussionThreadSheet
import info.lwb.feature.reader.ui.ActionRail
import info.lwb.feature.reader.ui.ActionRailItem
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.AudioBlock
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
import info.lwb.feature.reader.ui.YouTubeBlock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data holder for reader settings provided by caller (ViewModel layer wires flows & mutations)
internal data class ReaderSettingsState(
    val fontScale: Double,
    val lineHeight: Double,
    val onFontScaleChange: (Double) -> Unit,
    val onLineHeightChange: (Double) -> Unit,
)

// ArticleWebView moved to ui/ReaderWebView.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    articleTitle: String,
    htmlBody: String,
    settings: ReaderSettingsState,
    appearance: AppearanceState? = null,
    pages: List<Page>? = null,
    currentPageIndex: Int = 0,
    onPageChange: (Int) -> Unit = {},
    injectedCss: String? = null,
    modifier: Modifier = Modifier,
) {
    // Mutable UI state
    var fabVisible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
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

    BackHandler(enabled = true) {
        when {
            showAppearance -> {
                showAppearance = false
            }
            fabVisible -> {
                fabVisible = false
            }
            else -> {
                confirmExit = true
            }
        }
    }

    val blocks = remember(htmlBody) { parseHtmlToBlocks(htmlBody) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf(listOf<SearchHit>()) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600

    // Recompute matches whenever query or blocks change
    LaunchedEffect(searchQuery, blocks) {
        searchHits = buildSearchHits(
            pages,
            blocks,
            searchQuery,
        )
        currentSearchIndex = 0
    }

    Box(Modifier.fillMaxSize()) {
        val searchBarComposable: @Composable () -> Unit = {
            SearchBar(
                query = searchQuery,
                occurrences = searchHits.size,
                currentIndex = if (searchHits.isEmpty()) 0 else currentSearchIndex + 1,
                onPrev = {
                    if (searchHits.isNotEmpty()) {
                        currentSearchIndex = (currentSearchIndex - 1 + searchHits.size) % searchHits.size
                    }
                },
                onNext = {
                    if (searchHits.isNotEmpty()) {
                        currentSearchIndex = (currentSearchIndex + 1) % searchHits.size
                    }
                },
                onChange = { searchQuery = it },
            )
        }
        ReaderScaffold(
            modifier = modifier,
            articleTitle = articleTitle,
            settings = settings,
            onTapContent = { showFabTemporarily() },
            searchBar = searchBarComposable,
        ) { padding ->
            if (pages != null && pages.size > 1) {
                ReaderPagedContent(
                    articleTitle = articleTitle,
                    currentPageIndex = currentPageIndex,
                    pages = pages,
                    isWide = isWide,
                    settings = settings,
                    searchQuery = searchQuery,
                    searchHits = searchHits,
                    currentSearchIndex = currentSearchIndex,
                    onPageChange = onPageChange,
                    onAnnotationCreated = { /* handled inside */ },
                    currentSearchIndexProvider = { currentSearchIndex },
                )
            } else {
                ReaderSingleContent(
                    articleTitle = articleTitle,
                    blocks = blocks,
                    settings = settings,
                    searchQuery = searchQuery,
                    searchHits = searchHits,
                    currentSearchIndex = currentSearchIndex,
                    currentSearchIndexProvider = { currentSearchIndex },
                )
            }
        }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    ReaderScreenOverlays(
            appearance = appearance,
            showAppearance = showAppearance,
            onShowAppearance = {
                showAppearance = true
            },
            onDismissAppearance = {
                showAppearance = false
            },
            confirmExit = confirmExit,
            onDismissExit = {
                confirmExit = false
            },
            onExitConfirmed = {
                confirmExit = false
                backDispatcher?.onBackPressed()
            },
            fabVisible = fabVisible,
            onFabBookmark = {
                showFabTemporarily()
            },
            onFabListen = {
                showFabTemporarily()
            },
            onFabAppearance = {
                if (appearance != null) showAppearance = true
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaffold(
    modifier: Modifier,
    articleTitle: String,
    settings: ReaderSettingsState,
    onTapContent: () -> Unit,
    searchBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(articleTitle) }) },
        bottomBar = {
            ReaderControlsBar(settings = settings) { font, line -> 
                settings.onFontScaleChange(font)
                settings.onLineHeightChange(line)
            }
        },
    ) { padding -> 
        Column(
            Modifier
                .padding(padding)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTapContent() }) },
        ) {
            searchBar()
            content(padding)
        }
    }
}

// ---------------------------- Paged Mode ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPagedContent(
    articleTitle: String,
    currentPageIndex: Int,
    pages: List<Page>,
    isWide: Boolean,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndex: Int,
    onPageChange: (Int) -> Unit,
    onAnnotationCreated: (String) -> Unit,
    currentSearchIndexProvider: () -> Int,
) {
    // Pager header
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Page ${currentPageIndex + 1} / ${pages.size}",
            style = MaterialTheme.typography.labelMedium,
        )
        Row {
            androidx.compose.material3.Button(
                enabled = currentPageIndex > 0,
                onClick = { onPageChange((currentPageIndex - 1).coerceAtLeast(0)) },
            ) { Text("Prev") }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(
                enabled = currentPageIndex < pages.lastIndex,
                onClick = { onPageChange((currentPageIndex + 1).coerceAtMost(pages.lastIndex)) },
            ) { Text("Next") }
        }
    }

    val pageBlocks = remember(currentPageIndex, pages) { pages.getOrNull(currentPageIndex)?.blocks ?: emptyList() }
    val listState = rememberLazyListState()
    val scrollVm: ScrollViewModel = hiltViewModel()
    LaunchedEffect(articleTitle) { restoreListPosition(scrollVm, articleTitle, listState) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        scrollVm.saveList(articleTitle, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
    LaunchedEffect(currentSearchIndex, searchHits, currentPageIndex) {
        val hit = searchHits.getOrNull(currentSearchIndex)
        if (hit != null && hit.pageIndex == currentPageIndex) listState.animateScrollToItem(hit.blockIndex)
    }
    val toc: List<HeadingItem> = remember(pages) { buildHeadingItems(pages) }
    Row(Modifier.fillMaxSize()) {
        if (isWide && toc.isNotEmpty()) {
            TableOfContents(toc = toc, onSelect = { onPageChange(it.pageIndex) })
        }
        BlocksList(
            articleTitle = articleTitle,
            blocks = pageBlocks,
            settings = settings,
            searchQuery = searchQuery,
            searchHits = searchHits,
            currentSearchIndexProvider = currentSearchIndexProvider,
            pageIndex = currentPageIndex,
            listModifier = if (isWide) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize(),
        )
    }
}

// ---------------------------- Single Mode ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSingleContent(
    articleTitle: String,
    blocks: List<ContentBlock>,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndex: Int,
    currentSearchIndexProvider: () -> Int,
) {
    val listState = rememberLazyListState()
    val scrollVm: ScrollViewModel = hiltViewModel()
    LaunchedEffect(articleTitle) { restoreListPosition(scrollVm, articleTitle, listState) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        scrollVm.saveList(articleTitle, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
    LaunchedEffect(currentSearchIndex, searchHits) {
        val hit = searchHits.getOrNull(currentSearchIndex)
        if (hit != null) listState.animateScrollToItem(hit.blockIndex)
    }
    BlocksList(
        articleTitle = articleTitle,
        blocks = blocks,
        settings = settings,
        searchQuery = searchQuery,
        searchHits = searchHits,
        currentSearchIndexProvider = currentSearchIndexProvider,
        pageIndex = null,
        listModifier = Modifier.fillMaxSize(),
    )
}

// ---------------------------- Shared UI pieces ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlocksList(
    articleTitle: String,
    blocks: List<ContentBlock>,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndexProvider: () -> Int,
    pageIndex: Int?,
    listModifier: Modifier,
) {
    val listState = rememberLazyListState()
    // Provide the external state restored list (caller handled restore) by keying on articleTitle
    val deps = hiltViewModel<ReaderDeps>()
    var openAnnotationFor by remember { mutableStateOf<String?>(null) }
    LazyColumn(listModifier, state = listState) {
        items(blocks.size) { idx -> 
            val b = blocks[idx]
            when (b) {
                is ContentBlock.Paragraph -> ParagraphWithActions(
                    articleTitle = articleTitle,
                    block = b,
                    idx = idx,
                    settings = settings,
                    searchQuery = searchQuery,
                    searchHits = searchHits,
                    currentSearchIndexProvider = currentSearchIndexProvider,
                    pageIndex = pageIndex,
                    deps = deps,
                    onOpenAnnotation = { openAnnotationFor = it },
                )
                is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                is ContentBlock.Image -> AsyncImage(
                    model = b.url,
                    contentDescription = b.alt,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
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

@Composable
private fun ParagraphWithActions(
    articleTitle: String,
    block: ContentBlock.Paragraph,
    idx: Int,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndexProvider: () -> Int,
    pageIndex: Int?,
    deps: ReaderDeps,
    onOpenAnnotation: (String) -> Unit,
) {
    val currentSearchIndex = currentSearchIndexProvider()
    val activeRange =
        searchHits
            .getOrNull(currentSearchIndex)
            ?.takeIf { hit ->
                if (pageIndex != null) {
                    hit.pageIndex == pageIndex && hit.blockIndex == idx
                } else {
                    hit.blockIndex == idx
                }
            }
            ?.range
    Column(Modifier.fillMaxWidth()) {
        ParagraphBlock(
            text = block.text,
            query = searchQuery,
            settings = settings,
            activeRange = activeRange,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {
                val articleId = articleTitle
                val range = 0 to block.text.length
                deps.scope.launch {
                    val res = deps.addAnnotation(articleId, range.first, range.second, block.text.hashCode().toString())
                    if (res is info.lwb.core.common.Result.Success) onOpenAnnotation(res.data)
                }
            }) { Icon(Icons.Filled.Edit, contentDescription = "Annotate") }
        }
    }
}

@Composable
private fun TableOfContents(toc: List<HeadingItem>, onSelect: (HeadingItem) -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.width(220.dp).fillMaxHeight(),
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
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
                        .clickable { onSelect(h) }
                        .padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ReaderScreenOverlays(
    appearance: AppearanceState?,
    showAppearance: Boolean,
    onShowAppearance: () -> Unit,
    onDismissAppearance: () -> Unit,
    confirmExit: Boolean,
    onDismissExit: () -> Unit,
    onExitConfirmed: () -> Unit,
    fabVisible: Boolean,
    onFabBookmark: () -> Unit,
    onFabListen: () -> Unit,
    onFabAppearance: () -> Unit,
) {
    if (fabVisible) {
        ActionRail(
            modifier = Modifier.align(Alignment.BottomEnd),
            items = listOf(
                ActionRailItem(
                    icon = Icons.Filled.Settings,
                    label = "Appearance",
                    onClick = { onFabAppearance() },
                ),
                ActionRailItem(
                    icon = Icons.Filled.Edit,
                    label = "Bookmark",
                    onClick = { onFabBookmark() },
                ),
                ActionRailItem(
                    icon = Icons.Filled.PlayArrow,
                    label = "Listen",
                    onClick = { onFabListen() },
                ),
            ),
            mainIcon = Icons.Filled.Settings,
            mainContentDescription = "Reader actions",
            edgePadding = 16.dp,
        )
    }
    if (appearance != null) {
        ReaderAppearanceSheet(
            visible = showAppearance,
            state = appearance,
            onDismiss = onDismissAppearance,
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = onDismissExit,
            title = { Text("Leave reader?") },
            text = { Text("Are you sure you want to exit the reader?") },
            confirmButton = { TextButton(onClick = onExitConfirmed) { Text("Exit") } },
            dismissButton = { TextButton(onClick = onDismissExit) { Text("Cancel") } },
        )
    }
}

// ---------------------------- Utility ----------------------------

private suspend fun restoreListPosition(
    scrollVm: ScrollViewModel,
    articleTitle: String,
    state: androidx.compose.foundation.lazy.LazyListState,
) {
    val index = try {
        scrollVm.observeListIndex(articleTitle).first()
    } catch (_: Throwable) {
        0
    }
    val offset = try {
        scrollVm.observeListOffset(articleTitle).first()
    } catch (_: Throwable) {
        0
    }
    if (index > 0 || offset > 0) state.scrollToItem(index, offset)
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
    val scaledFont =
        (baseStyle.fontSize.value * settings.fontScale)
            .coerceAtLeast(10.0)
            .sp
    val scaledLineHeight =
        (baseStyle.lineHeight.value * settings.lineHeight)
            .coerceAtLeast(12.0)
            .sp
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

// Media blocks moved to ui/ReaderBlocks.kt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderControlsBar(settings: ReaderSettingsState, onChange: (Double, Double) -> Unit) {
    Surface(
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                "Font: ${"%.2f".format(settings.fontScale)}  Line: ${"%.2f".format(settings.lineHeight)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontScale.toFloat(),
                    onValueChange = {
                        onChange(
                            it.toDouble().coerceIn(0.8, 1.6),
                            settings.lineHeight,
                        )
                    },
                    valueRange = 0.8f..1.6f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = settings.lineHeight.toFloat(),
                    onValueChange = {
                        onChange(
                            settings.fontScale,
                            it.toDouble().coerceIn(1.0, 2.0),
                        )
                    },
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
