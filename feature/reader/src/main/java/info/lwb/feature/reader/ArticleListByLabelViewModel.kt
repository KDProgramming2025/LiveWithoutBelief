/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel that loads and exposes a list of [Article]s associated with a specific label.
 *
 * It keeps a simple immutable [UiState] exposed as a [StateFlow]. Loading is performed via
 * [GetArticlesByLabelUseCase] and any failure is surfaced as a user‑readable error message while
 * clearing previous results. A request for the same label while a load is already completed is
 * ignored to avoid redundant work.
 */
@HiltViewModel
class ArticleListByLabelViewModel @Inject constructor(private val getByLabel: GetArticlesByLabelUseCase) : ViewModel() {
    /**
     * UI snapshot for the screen.
     * @property label The label whose articles are displayed.
     * @property loading True while an initial or non user-initiated load is active.
     * @property items Loaded articles or empty when none / during loading / on error.
     * @property error Optional user‑facing error description when a load fails.
     * @property refreshing True while a user pull-to-refresh is in progress (items retained).
     */
    data class UiState(
        val label: String = "",
        val loading: Boolean = false,
        val items: List<Article> = emptyList(),
        val error: String? = null,
        val refreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())

    /** Publicly observable state for UI composition. */
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Transient messages for recoverable failures (network, parsing) while previously loaded
     * items are retained on screen. Consumed by UI for snackbar display.
     */
    val snackbar: SharedFlow<String> = _snackbar

    /**
     * Load articles for [label]. If the requested label is already current and a load is not
     * in progress the call is a no‑op.
     */
    fun load(label: String) {
        if (label == _state.value.label && !_state.value.loading && !_state.value.refreshing) {
            return
        }
        _state.value = _state.value.copy(label = label, loading = true, error = null, refreshing = false)
        fetch(label)
    }

    /** Force a refresh even if the current label matches; shows pull-to-refresh indicator. */
    fun refresh() {
        val current = _state.value.label
        val blocked = current.isBlank() || _state.value.refreshing || _state.value.loading
        if (blocked) {
            return
        }
        _state.value = _state.value.copy(refreshing = true, error = null)
        fetch(current, isRefresh = true)
    }

    private fun fetch(label: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            val result = runCatching { getByLabel(label) }
            result.onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    items = list,
                    error = null,
                )
            }
            result.onFailure { t ->
                val currentItems = _state.value.items
                val message = t.message ?: "Failed to load"
                if (currentItems.isNotEmpty()) {
                    // Keep existing items; surface transient message only.
                    _snackbar.tryEmit(message)
                    val newRefreshing = if (isRefresh) {
                        false
                    } else {
                        _state.value.refreshing
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = newRefreshing,
                    )
                    // Do not clear items or set error; silent fallback.
                } else {
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        items = emptyList(),
                        error = message,
                    )
                }
            }
        }
    }
}
