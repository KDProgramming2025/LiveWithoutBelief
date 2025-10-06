/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.articles.internal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase
import info.lwb.core.model.Article
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MSG_FAILED_LOAD = "Failed to load"
private const val MSG_REFRESH_FAILED = "Refresh failed"
private const val TAG = "ArticlesVM"
// Initial refresh timeout constant removed after moving prefetch logic into repository.

/** Internal ViewModel exposing article collections and article content streams for the Articles feature. */
@HiltViewModel
internal class ArticlesViewModel @Inject constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val getArticlesByLabelUseCase: GetArticlesByLabelUseCase,
) : ViewModel() {
    private val _articles = MutableStateFlow<Result<List<Article>>>(Result.Loading)
    val articles: StateFlow<Result<List<Article>>> = _articles.asStateFlow()

    @Volatile
    private var started = false

    // Removed explicit initial refresh flags; repository now handles first-install prefetch.

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar

    data class ArticlesListUiState(
        val label: String = "",
        val loading: Boolean = false,
        val items: List<Article> = emptyList(),
        val error: String? = null,
        val initializing: Boolean = true, // true until first non-empty success or terminal empty after refresh
    )

    private val _listState = MutableStateFlow(ArticlesListUiState())
    val listState: StateFlow<ArticlesListUiState> = _listState.asStateFlow()

    init {
        // Automatically start collection and initial refresh.
        // Host routes no longer need to invoke ensureLoaded manually.
        Log.d(TAG, "init:auto-start")
        ensureLoaded()
    }

    fun ensureLoaded() {
        if (started) {
            Log.d(TAG, "ensureLoaded:already-started")
            return
        }
        Log.d(TAG, "ensureLoaded:start collecting & trigger initial refresh")
        started = true
        collectArticles()
    }

    private fun collectArticles() = viewModelScope.launch {
        Log.d(TAG, "collectArticles:start")
        getArticlesUseCase().collect { result ->
            when (result) {
                is Result.Loading -> {
                    Log.d(TAG, "collectArticles:emission=Loading")
                    handleArticlesLoadingForList()
                }
                is Result.Success -> {
                    Log.d(TAG, "collectArticles:emission=Success size=" + result.data.size)
                    handleArticlesSuccessForList(result)
                }
                is Result.Error -> {
                    Log.d(TAG, "collectArticles:emission=Error msg=" + (result.throwable.message ?: "?"))
                    handleArticlesErrorForList(result)
                }
            }
        }
    }

    private fun handleArticlesLoadingForList() {
        handleArticlesLoading(Result.Loading)
        val ls = _listState.value
        if (ls.label.isBlank() && ls.items.isEmpty()) {
            _listState.value = ls.copy(loading = true, error = null)
        }
    }

    private fun handleArticlesSuccessForList(result: Result.Success<List<Article>>) {
        _articles.value = result
        _listState.value = _listState.value.copy(
            loading = false,
            items = result.data,
            error = null,
            initializing = false,
        )
    }

    private fun handleArticlesErrorForList(result: Result.Error) {
        handleArticlesError(result)
        if (_listState.value.label.isBlank()) {
            val listState = _listState.value
            val message = result.throwable.message ?: MSG_FAILED_LOAD
            if (listState.items.isNotEmpty()) {
                _snackbar.tryEmit(message)
                _listState.value = listState.copy(loading = false, initializing = false)
            } else {
                _listState.value = listState.copy(loading = false, error = message, initializing = false)
            }
        }
    }

    private fun handleArticlesLoading(result: Result.Loading) {
        val current = _articles.value
        if (current !is Result.Success) {
            _articles.value = result
        }
    }

    private fun handleArticlesError(result: Result.Error) {
        val current = _articles.value
        if (current is Result.Success && current.data.isNotEmpty()) {
            _snackbar.tryEmit(result.throwable.message ?: MSG_REFRESH_FAILED)
        } else {
            _articles.value = result
        }
    }

    fun refreshArticles(
        onComplete: ((emptyAfter: Boolean) -> Unit)? = null, // callback retained for UI refresh list
    ) {
        if (_refreshing.value) {
            Log.d(TAG, "refresh:ignored already refreshing")
            // If already refreshing we can't know emptiness yet; only the active refresh will callback.
            return
        }
        Log.d(TAG, "refresh:start trigger")
        _refreshing.value = true
        viewModelScope.launch {
            try {
                Log.d(TAG, "refresh:invoke useCase")
                refreshArticlesUseCase()
                Log.d(TAG, "refresh:useCase completed")
            } catch (ce: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "refresh:cancelled")
                throw ce
            } catch (_: Throwable) {
                // ignore
                Log.d(TAG, "refresh:failed silently")
            } finally {
                _refreshing.value = false
                Log.d(TAG, "refresh:finalize refreshing=" + _refreshing.value)
                val emptyNow = (_articles.value as? Result.Success)?.data?.isEmpty() ?: true
                onComplete?.invoke(emptyNow)
            }
        }
    }

    fun loadList(label: String) {
        val current = _listState.value
        val alreadyLoaded = current.items.isNotEmpty() && !current.loading && !_refreshing.value
        if (current.label == label && alreadyLoaded) {
            return
        }
        _listState.value = current.copy(
            label = label,
            loading = true,
            error = null,
            initializing = if (label.isBlank()) {
                current.initializing
            } else {
                true
            },
        )
        if (label.isBlank()) {
            ensureLoaded()
        } else {
            fetchLabel(label)
        }
    }

    fun refreshList() {
        val current = _listState.value
        Log.d(TAG, "refreshList:entered label=" + current.label)
        Log.d(TAG, "refreshList:state loading=" + current.loading + " refreshing=" + _refreshing.value)
        if (_refreshing.value || current.loading) {
            Log.d(TAG, "refreshList:ignored busy")
            return
        }
        val f = current.label
        if (f.isBlank()) {
            Log.d(TAG, "refreshList:root -> delegate refreshArticles")
            refreshArticles()
            return
        }
        if (f.isNotBlank()) {
            Log.d(TAG, "refreshList:label -> force network then fetchLabel")
            _listState.value = current.copy(error = null)
            viewModelScope.launch {
                // Trigger a manifest refresh first so label results reflect latest data.
                runCatching {
                    Log.d(TAG, "refreshList:label -> invoking refreshArticlesUseCase before label fetch")
                    refreshArticlesUseCase()
                    Log.d(TAG, "refreshList:label -> manifest refreshed")
                }
                fetchLabel(f)
            }
        }
    }

    private fun fetchLabel(label: String) = viewModelScope.launch {
        try {
            val list = getArticlesByLabelUseCase(label)
            _listState.value = _listState.value.copy(
                loading = false,
                items = list,
                error = null,
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: IllegalStateException) {
            val message = t.message ?: MSG_FAILED_LOAD
            val existing = _listState.value.items
            if (existing.isNotEmpty()) {
                _snackbar.tryEmit(message)
                _listState.value = _listState.value.copy(
                    loading = false,
                )
            } else {
                _listState.value = _listState.value.copy(
                    loading = false,
                    items = emptyList(),
                    error = message,
                )
            }
        } finally {
            // nothing
        }
    }
}
