/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import info.lwb.feature.annotations.DiscussionThreadSheet
import info.lwb.feature.reader.ui.AudioBlock
import info.lwb.feature.reader.ui.HeadingBlock
import info.lwb.feature.reader.ui.ParagraphBlock
import info.lwb.feature.reader.ui.YouTubeBlock
import kotlinx.coroutines.launch

/**
 * Hosts either paged reader content or a single scrolling list of blocks depending on the
 * provided page list.
 */
@Composable
internal fun ReaderScreenContent(
    articleTitle: String,
    pages: List<Page>?,
    currentPageIndex: Int,
    isWide: Boolean,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndex: Int,
    onPageChange: (Int) -> Unit,
    currentSearchIndexProvider: () -> Int,
    blocks: List<ContentBlock>,
) {
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
            currentSearchIndexProvider = currentSearchIndexProvider,
        )
    } else {
        ReaderSingleContent(
            articleTitle = articleTitle,
            blocks = blocks,
            settings = settings,
            searchQuery = searchQuery,
            searchHits = searchHits,
            currentSearchIndex = currentSearchIndex,
            currentSearchIndexProvider = currentSearchIndexProvider,
        )
    }
}

// Highlight + typography scaling constants extracted from magic numbers previously inline
// Removed unused constants (highlight alphas, min font/line height, toc width, spacing) flagged by detekt
private val IMAGE_BLOCK_HEIGHT = 220.dp
private val BLOCK_SPACING = 12.dp
private val TOC_SPACING = 2.dp

@Composable
internal fun ReaderPagedContent(
    articleTitle: String,
    currentPageIndex: Int,
    pages: List<Page>,
    isWide: Boolean,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndex: Int,
    onPageChange: (Int) -> Unit,
    currentSearchIndexProvider: () -> Int,
) {
    ReaderPagedHeader(
        currentPageIndex = currentPageIndex,
        pages = pages,
        onPageChange = onPageChange,
    )
    ReaderPagedBody(
        articleTitle = articleTitle,
        currentPageIndex = currentPageIndex,
        pages = pages,
        isWide = isWide,
        settings = settings,
        searchQuery = searchQuery,
        searchHits = searchHits,
        currentSearchIndex = currentSearchIndex,
        onPageChange = onPageChange,
        currentSearchIndexProvider = currentSearchIndexProvider,
    )
}

@Composable
private fun ReaderPagedHeader(currentPageIndex: Int, pages: List<Page>, onPageChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Page ${currentPageIndex + 1} / ${pages.size}",
            style = MaterialTheme.typography.labelMedium,
        )
        Row {
            androidx.compose.material3.Button(
                enabled = currentPageIndex > 0,
                onClick = { onPageChange((currentPageIndex - 1).coerceAtLeast(0)) },
            ) { Text(text = "Prev") }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Button(
                enabled = currentPageIndex < pages.lastIndex,
                onClick = { onPageChange((currentPageIndex + 1).coerceAtMost(pages.lastIndex)) },
            ) { Text(text = "Next") }
        }
    }
}

@Composable
private fun ReaderPagedBody(
    articleTitle: String,
    currentPageIndex: Int,
    pages: List<Page>,
    isWide: Boolean,
    settings: ReaderSettingsState,
    searchQuery: String,
    searchHits: List<SearchHit>,
    currentSearchIndex: Int,
    onPageChange: (Int) -> Unit,
    currentSearchIndexProvider: () -> Int,
) {
    val pageBlocks = remember(currentPageIndex, pages) {
        pages
            .getOrNull(currentPageIndex)
            ?.blocks
            ?: emptyList()
    }
    val listState = rememberLazyListState()
    val scrollVm: ScrollViewModel = hiltViewModel()
    LaunchedEffect(articleTitle) {
        restoreListPosition(
            scrollVm = scrollVm,
            articleTitle = articleTitle,
            state = listState,
        )
    }
    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
    ) {
        scrollVm.saveList(
            articleId = articleTitle,
            index = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
        )
    }
    LaunchedEffect(currentSearchIndex, searchHits, currentPageIndex) {
        val hit = searchHits.getOrNull(currentSearchIndex)
        if (hit != null && hit.pageIndex == currentPageIndex) {
            listState.animateScrollToItem(hit.blockIndex)
        }
    }
    val toc: List<HeadingItem> = remember(pages) { buildHeadingItems(pages) }
    Row(modifier = Modifier.fillMaxSize()) {
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
            listModifier = if (isWide) {
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            } else {
                Modifier
                    .fillMaxSize()
            },
        )
    }
}

@Composable
internal fun ReaderSingleContent(
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
    LaunchedEffect(articleTitle) {
        restoreListPosition(
            scrollVm = scrollVm,
            articleTitle = articleTitle,
            state = listState,
        )
    }
    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
    ) {
        scrollVm.saveList(
            articleId = articleTitle,
            index = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
        )
    }
    LaunchedEffect(currentSearchIndex, searchHits) {
        val hit = searchHits.getOrNull(currentSearchIndex)
        if (hit != null) {
            listState.animateScrollToItem(hit.blockIndex)
        }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun BlocksList(
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
    val deps = hiltViewModel<ReaderDeps>()
    var openAnnotationFor by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = listModifier, state = listState) {
        items(blocks.size) { idx ->
            val b = blocks[idx]
            when (b) {
                is ContentBlock.Paragraph -> {
                    ParagraphWithActions(
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
                }
                is ContentBlock.Heading -> {
                    HeadingBlock(level = b.level, text = b.text)
                }
                is ContentBlock.Image -> {
                    AsyncImage(
                        model = b.url,
                        contentDescription = b.alt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IMAGE_BLOCK_HEIGHT),
                    )
                }
                is ContentBlock.Audio -> {
                    AudioBlock(url = b.url)
                }
                is ContentBlock.YouTube -> {
                    YouTubeBlock(videoId = b.videoId)
                }
            }
            Spacer(modifier = Modifier.height(BLOCK_SPACING))
        }
    }
    openAnnotationFor?.let { annotationId ->
        ModalBottomSheet(onDismissRequest = { openAnnotationFor = null }) {
            DiscussionThreadSheet(annotationId = annotationId)
        }
    }
}

@Composable
internal fun ParagraphWithActions(
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
    val activeRange = searchHits
        .getOrNull(currentSearchIndex)
        ?.takeIf { hit ->
            if (pageIndex != null) {
                hit.pageIndex == pageIndex && hit.blockIndex == idx
            } else {
                hit.blockIndex == idx
            }
        }?.range
    Column(modifier = Modifier.fillMaxWidth()) {
        ParagraphBlock(
            text = block.text,
            query = searchQuery,
            settings = settings,
            activeRange = activeRange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.IconButton(
                onClick = {
                    val articleId = articleTitle
                    val range = 0 to block.text.length
                    deps.scope.launch {
                        val res = deps.addAnnotation(
                            articleId = articleId,
                            start = range.first,
                            end = range.second,
                            hash = block.text.hashCode().toString(),
                        )
                        if (res is info.lwb.core.common.Result.Success) {
                            onOpenAnnotation(res.data)
                        }
                    }
                },
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Annotate",
                )
            }
        }
    }
}

@Composable
internal fun TableOfContents(toc: List<HeadingItem>, onSelect: (HeadingItem) -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.width(220.dp).fillMaxHeight(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            items(toc.size) { i ->
                val h = toc[i]
                Text(
                    text = h.text,
                    style = when (h.level) {
                        1 -> {
                            MaterialTheme.typography.titleMedium
                        }
                        2 -> {
                            MaterialTheme.typography.titleSmall
                        }
                        else -> {
                            MaterialTheme.typography.bodySmall
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(h) }
                        .padding(vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(TOC_SPACING))
            }
        }
    }
}
