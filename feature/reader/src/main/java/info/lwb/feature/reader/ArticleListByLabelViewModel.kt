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
import kotlinx.coroutines.flow.StateFlow
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
     * @property loading True while a load operation is active.
     * @property items Loaded articles or empty when none / during loading / on error.
     * @property error Optional user‑facing error description when a load fails.
     */
    data class UiState(
        val label: String = "",
        val loading: Boolean = false,
        val items: List<Article> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())

    /** Publicly observable state for UI composition. */
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Load articles for [label]. If the requested label is already current and a load is not
     * in progress the call is a no‑op.
     */
    fun load(label: String) {
        if (label == _state.value.label && !_state.value.loading) {
            return
        }
        _state.value = _state.value.copy(label = label, loading = true, error = null)
        viewModelScope.launch {
            val result = runCatching { getByLabel(label) }
            result.onSuccess { list ->
                _state.value = _state.value.copy(loading = false, items = list, error = null)
            }
            result.onFailure { t ->
                _state.value = _state.value.copy(
                    loading = false,
                    items = emptyList(),
                    error = t.message ?: "Failed to load",
                )
            }
        }
    }
}
