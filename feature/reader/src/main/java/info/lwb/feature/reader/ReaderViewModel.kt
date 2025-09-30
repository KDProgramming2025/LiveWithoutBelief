/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.ReadingProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Internal ViewModel backing the Reader screen, exposes article pagination state and appearance settings.
 * Not part of the public feature API; use [ReaderByIdRoute] to interact with the Reader UI.
 */
@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    private val progressRepo: ReadingProgressRepository,
    private val settingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val articleIdState = MutableStateFlow("")
    private val htmlBodyState = MutableStateFlow("")
    private val blocksState = MutableStateFlow<List<ContentBlock>>(emptyList())
    private val pageIndexState = MutableStateFlow(0)

    val fontScale = settingsRepository.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)
    val lineHeight = settingsRepository.lineHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.2)
    val background = settingsRepository.background.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReaderSettingsRepository.ReaderBackground.Paper,
    )

    // Reverted to original inline chain layout
    private val pagesState = combine(blocksState, fontScale) { blocks, scale ->
        paginate(blocks, fontScale = scale)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val headings = pagesState.map { buildHeadingItems(it) }

    private val appearanceState = combine(fontScale, lineHeight, background) { f, l, b -> Triple(f, l, b) }

    val uiState: StateFlow<ReaderUiState> = combine(
        articleIdState,
        pagesState,
        pageIndexState,
        appearanceState,
    ) { articleId, pages, pageIndex, appearance ->
        val (fScale, lHeight, bg) = appearance
        ReaderUiState(
            articleId = articleId,
            pages = pages,
            currentPageIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            fontScale = fScale,
            lineHeight = lHeight,
            background = bg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.EMPTY)

    fun loadArticle(articleId: String, htmlBody: String) {
        articleIdState.value = articleId
        htmlBodyState.value = htmlBody
        blocksState.value = parseHtmlToBlocks(htmlBody)
        // Observe existing progress
        viewModelScope.launch {
            progressRepo.observe(articleId).collect { p ->
                if (p != null && p.totalPages > 0) {
                    pageIndexState.value = p.pageIndex.coerceIn(0, p.totalPages - 1)
                }
            }
        }
    }

    fun onPageChange(newIndex: Int) {
        pageIndexState.value = newIndex
        persistProgress()
    }

    fun onFontScaleChange(v: Double) = viewModelScope.launch { settingsRepository.setFontScale(v) }
    fun onLineHeightChange(v: Double) = viewModelScope.launch { settingsRepository.setLineHeight(v) }
    fun onBackgroundChange(bg: ReaderSettingsRepository.ReaderBackground) = viewModelScope.launch {
        settingsRepository.setBackground(bg)
    }

    private fun persistProgress() {
        val pages = pagesState.value
        val articleId = articleIdState.value
        if (articleId.isBlank() || pages.isEmpty()) {
            return
        }
        viewModelScope.launch {
            progressRepo.update(articleId, pageIndexState.value, pages.size)
        }
    }
}

/** UI state snapshot for the Reader screen (internal). */
internal data class ReaderUiState(
    val articleId: String,
    val pages: List<Page>,
    val currentPageIndex: Int,
    val fontScale: Double,
    val lineHeight: Double,
    val background: ReaderSettingsRepository.ReaderBackground,
) {
    companion object {
        val EMPTY = ReaderUiState(
            articleId = "",
            pages = emptyList(),
            currentPageIndex = 0,
            fontScale = 1.0,
            lineHeight = 1.2,
            background = ReaderSettingsRepository.ReaderBackground.Paper,
        )
    }
}
