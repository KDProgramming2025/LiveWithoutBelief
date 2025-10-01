/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetMenuUseCase
import info.lwb.core.domain.RefreshMenuUseCase
import info.lwb.core.model.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * ViewModel for the Home screen; exposes menu items and tracks loading/error state.
 * It loads the current menu via a flow and triggers a background refresh.
*/
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMenu: GetMenuUseCase,
    private val refreshMenu: RefreshMenuUseCase,
    /** Base URL for API requests used by UI links. */
    @Named("apiBaseUrl") val apiBaseUrl: String,
    /** Base URL for uploaded assets (images, etc.). */
    @Named("uploadsBaseUrl") val uploadsBaseUrl: String,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    /** Public state flow representing UI state (Loading, Success, Error). */
    val state: StateFlow<HomeUiState> = _state

    /** Begin collecting menu items and trigger a background refresh. Safe to call multiple times. */
    fun load() {
        viewModelScope.launch {
            getMenu().collectLatest { res ->
                when (res) {
                    is Result.Loading -> {
                        _state.value = HomeUiState.Loading
                    }
                    is Result.Success -> {
                        _state.value = HomeUiState.Success(res.data)
                    }
                    is Result.Error -> {
                        _state.value = HomeUiState.Error(res.throwable.message ?: "Unknown error")
                    }
                }
            }
        }
        viewModelScope.launch { runCatching { refreshMenu() } }
    }
}

/** UI state hierarchy for the Home screen. */
sealed interface HomeUiState {
    /** Loading state while awaiting data. */
    data object Loading : HomeUiState

    /** Successful menu retrieval with list of menu items. */
    data class Success(
        /** The menu items to display. */
        val items: List<MenuItem>,
    ) : HomeUiState

    /** Error state containing an end-user message. */
    data class Error(
        /** User-friendly error message. */
        val message: String,
    ) : HomeUiState
}
