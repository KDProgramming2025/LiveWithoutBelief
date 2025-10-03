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
import info.lwb.feature.home.network.ConnectivityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val connectivityProvider: ConnectivityProvider,
    /** Base URL for API requests used by UI links. */
    @Named("apiBaseUrl") val apiBaseUrl: String,
    /** Base URL for uploaded assets (images, etc.). */
    @Named("uploadsBaseUrl") val uploadsBaseUrl: String,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    @Volatile private var started = false

    private val _refreshing = MutableStateFlow(false)

    /** Exposed flag for UI pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Public state flow representing UI state (Loading, Success, Error). */
    val state: StateFlow<HomeUiState> = _state

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Hot flow of transient textual notifications (e.g., background refresh failures while cache
     * is shown). Consumed by UI to display a snackbar without altering the primary content state.
     */
    val snackbar: SharedFlow<String> = _snackbar

    /**
     * Begin collecting menu items and trigger an initial background refresh.
     * Subsequent invocations are ignored to avoid unnecessary reloads when returning to Home.
     */
    fun ensureLoaded() {
        if (started) {
            return
        }
        started = true
        viewModelScope.launch {
            getMenu().collectLatest { res ->
                when (res) {
                    is Result.Loading -> {
                        // Do not override an already successful state with a loading spinner; keeps
                        // cached data visible during background refresh attempts.
                        if (_state.value !is HomeUiState.Success) {
                            _state.value = HomeUiState.Loading
                        }
                    }
                    is Result.Success -> {
                        _state.value = HomeUiState.Success(res.data)
                    }
                    is Result.Error -> {
                        val current = _state.value
                        val userMessage = classifyError()
                        if (current is HomeUiState.Success && current.items.isNotEmpty()) {
                            // Only emit sanitized message in snackbar
                            _snackbar.tryEmit(userMessage)
                        } else {
                            _state.value = HomeUiState.Error(userMessage)
                        }
                    }
                }
            }
        }
        viewModelScope.launch { runCatching { refreshMenu() } }
    }

    /** Explicit refresh entry point (e.g., future pull-to-refresh) still allowed. */
    fun forceRefresh() {
        // Allow refresh in any state (including Error or empty) but avoid parallel refreshes
        if (_refreshing.value) {
            return
        }
        _refreshing.value = true
        viewModelScope.launch {
            runCatching { refreshMenu() }
            _refreshing.value = false
        }
    }

    /** Alias used by UI pull-to-refresh to allow refreshing during error/empty states. */
    fun refresh() = forceRefresh()

    private fun classifyError(): String {
        val online = connectivityProvider.isOnline()
        return if (!online) {
            "Internet connection is disabled"
        } else {
            "Failed to connect to server"
        }
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
