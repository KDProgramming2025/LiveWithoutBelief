/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Reader appearance state holder (pagination removed).

/**
 * Internal ViewModel backing the Reader screen, exposes article appearance settings.
 * Not part of the public feature API; use [ReaderByIdRoute] to interact with the Reader UI.
 */
@HiltViewModel
internal class ReaderSessionViewModel @Inject constructor(private val settingsRepository: ReaderSettingsRepository) :
    ViewModel() {
    private companion object {
        // StateIn subscription timeout milliseconds
        private const val WHILE_SUB_TIMEOUT_MS = 5_000L
    }

    private val articleIdState = MutableStateFlow("")
    private var loadedArticleId: String? = null

    val fontScale = settingsRepository.fontScale.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DEFAULT_FONT_SCALE,
    )
    val lineHeight = settingsRepository.lineHeight.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DEFAULT_LINE_HEIGHT,
    )
    val background = settingsRepository.background.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReaderSettingsRepository.ReaderBackground.Paper,
    )

    private val appearanceState = combine(fontScale, lineHeight, background) { f, l, b -> Triple(f, l, b) }

    val uiState: StateFlow<ReaderUiState> = combine(
        articleIdState,
        appearanceState,
    ) { articleId, appearance ->
        val (fScale, lHeight, bg) = appearance
        ReaderUiState(
            articleId = articleId,
            fontScale = fScale,
            lineHeight = lHeight,
            background = bg,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(WHILE_SUB_TIMEOUT_MS),
        ReaderUiState.EMPTY,
    )

    fun loadArticle(articleId: String) {
        if (loadedArticleId == articleId) {
            return
        }
        articleIdState.value = articleId
        loadedArticleId = articleId
    }

    fun onFontScaleChange(v: Double) = viewModelScope.launch { settingsRepository.setFontScale(v) }

    fun onLineHeightChange(v: Double) = viewModelScope.launch { settingsRepository.setLineHeight(v) }

    fun onBackgroundChange(bg: ReaderSettingsRepository.ReaderBackground) = viewModelScope.launch {
        settingsRepository.setBackground(bg)
    }
}

/** UI state snapshot for the Reader screen (internal). */
internal data class ReaderUiState(
    val articleId: String,
    val fontScale: Double,
    val lineHeight: Double,
    val background: ReaderSettingsRepository.ReaderBackground,
) {
    companion object {
        val EMPTY = ReaderUiState(
            articleId = "",
            fontScale = DEFAULT_FONT_SCALE,
            lineHeight = DEFAULT_LINE_HEIGHT,
            background = ReaderSettingsRepository.ReaderBackground.Paper,
        )
    }
}
