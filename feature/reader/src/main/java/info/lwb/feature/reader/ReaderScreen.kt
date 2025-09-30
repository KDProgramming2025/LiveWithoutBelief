/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

// Reader screen orchestrator after modularization
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import info.lwb.feature.reader.ui.AppearanceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Constants extracted from magic numbers
private const val FAB_AUTO_HIDE_DELAY_MS = 5000L
private const val WIDE_LAYOUT_MIN_WIDTH_DP = 600

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
    modifier: Modifier = Modifier,
) {
    val state =
        rememberReaderScreenState(
            htmlBody = htmlBody,
            pages = pages,
        )

    BackHandler(enabled = true) {
        when {
            state.showAppearance -> {
                state.showAppearance = false
            }
            state.fabVisible -> {
                state.fabVisible = false
            }
            else -> {
                state.confirmExit = true
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP

    Box(Modifier.fillMaxSize()) {
        val searchBarComposable: @Composable () -> Unit = {
            ReaderSearchBar(
                query = state.searchQuery,
                hits = state.searchHits.size,
                currentIndex = state.currentSearchIndex,
                onQueryChange = { state.searchQuery = it },
                onPrev = { state.jumpPrev() },
                onNext = { state.jumpNext() },
            )
        }
        ReaderScaffold(
            modifier = modifier,
            articleTitle = articleTitle,
            settings = settings,
            onTapContent = { state.showFabTemporarily() },
            searchBar = searchBarComposable,
        ) { padding ->
            ReaderScreenContent(
                articleTitle = articleTitle,
                pages = pages,
                currentPageIndex = currentPageIndex,
                isWide = isWide,
                settings = settings,
                searchQuery = state.searchQuery,
                searchHits = state.searchHits,
                currentSearchIndex = state.currentSearchIndex,
                onPageChange = onPageChange,
                currentSearchIndexProvider = { state.currentSearchIndex },
                blocks = state.blocks,
            )
        }
        ReaderOverlaysHost(
            state = state,
            appearance = appearance,
        )
    }
}

@Composable
private fun BoxScope.ReaderOverlaysHost(state: ReaderScreenState, appearance: AppearanceState?) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    ReaderScreenOverlays(
        appearance = appearance,
        showAppearance = state.showAppearance,
        onShowAppearance = { state.showAppearance = true },
        onDismissAppearance = { state.showAppearance = false },
        confirmExit = state.confirmExit,
        onDismissExit = { state.confirmExit = false },
        onExitConfirmed = {
            state.confirmExit = false
            backDispatcher?.onBackPressed()
        },
        fabVisible = state.fabVisible,
        onFabBookmark = { state.showFabTemporarily() },
        onFabListen = { state.showFabTemporarily() },
        onFabAppearance = {
            if (appearance != null) {
                state.showAppearance = true
            }
        },
    )
}

@Composable
private fun rememberReaderScreenState(htmlBody: String, pages: List<Page>?): ReaderScreenState {
    val scope = rememberCoroutineScope()
    var fabVisible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf(listOf<SearchHit>()) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    val blocks = remember(htmlBody) { parseHtmlToBlocks(htmlBody) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleHideFab() {
        hideJob?.cancel()
        hideJob =
            scope.launch {
                delay(FAB_AUTO_HIDE_DELAY_MS)
                fabVisible = false
            }
    }

    LaunchedEffect(Unit) { scheduleHideFab() }
    DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }

    // Recompute search hits when query or blocks/pagination change
    LaunchedEffect(searchQuery, blocks) {
        searchHits = buildSearchHits(pages, blocks, searchQuery)
        currentSearchIndex = 0
    }

    return remember {
        ReaderScreenState(
            blocksProvider = { blocks },
            fabVisibleProvider = { fabVisible },
            setFabVisible = { fabVisible = it },
            showAppearanceProvider = { showAppearance },
            setShowAppearance = { showAppearance = it },
            confirmExitProvider = { confirmExit },
            setConfirmExit = { confirmExit = it },
            searchQueryProvider = { searchQuery },
            setSearchQuery = { searchQuery = it },
            searchHitsProvider = { searchHits },
            currentIndexProvider = { currentSearchIndex },
            setCurrentIndex = { currentSearchIndex = it },
            onScheduleHideFab = { scheduleHideFab() },
        )
    }
}

// Blank line added before class per style rule
private class ReaderScreenState(
    private val blocksProvider: () -> List<ContentBlock>,
    private val fabVisibleProvider: () -> Boolean,
    private val setFabVisible: (Boolean) -> Unit,
    private val showAppearanceProvider: () -> Boolean,
    private val setShowAppearance: (Boolean) -> Unit,
    private val confirmExitProvider: () -> Boolean,
    private val setConfirmExit: (Boolean) -> Unit,
    private val searchQueryProvider: () -> String,
    private val setSearchQuery: (String) -> Unit,
    private val searchHitsProvider: () -> List<SearchHit>,
    private val currentIndexProvider: () -> Int,
    private val setCurrentIndex: (Int) -> Unit,
    private val onScheduleHideFab: () -> Unit,
) {
    val blocks get() = blocksProvider()
    var fabVisible: Boolean
        get() = fabVisibleProvider()
        set(value) {
            setFabVisible(value)
        }
    var showAppearance: Boolean
        get() = showAppearanceProvider()
        set(value) {
            setShowAppearance(value)
        }
    var confirmExit: Boolean
        get() = confirmExitProvider()
        set(value) {
            setConfirmExit(value)
        }
    var searchQuery: String
        get() = searchQueryProvider()
        set(value) {
            setSearchQuery(value)
        }
    val searchHits get() = searchHitsProvider()
    var currentSearchIndex: Int
        get() = currentIndexProvider()
        set(value) {
            setCurrentIndex(value)
        }

    fun showFabTemporarily() {
        fabVisible = true
        onScheduleHideFab()
    }

    fun jumpPrev() {
        val total = searchHits.size
        if (total > 0) {
            currentSearchIndex = (currentSearchIndex - 1 + total) % total
        }
    }

    fun jumpNext() {
        val total = searchHits.size
        if (total > 0) {
            currentSearchIndex = (currentSearchIndex + 1) % total
        }
    }
}

@Composable
private fun ReaderSearchBar(
    query: String,
    hits: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    SearchBar(
        query = query,
        occurrences = hits,
        currentIndex =
        if (hits == 0) {
            0
        } else {
            currentIndex + 1
        },
        onPrev = onPrev,
        onNext = onNext,
        onChange = onQueryChange,
    )
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
        androidx.compose.foundation.layout.Column(
            Modifier
                .padding(padding)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTapContent() }) },
        ) {
            searchBar()
            content(padding)
        }
    }
}

// Duplicated paged/single content & overlays removed; authoritative versions live in
// ReaderContent.kt and ReaderOverlays.kt. Utility helpers relocated to ReaderDeps.kt.
// Previews live in ReaderScreenPreview.kt.
