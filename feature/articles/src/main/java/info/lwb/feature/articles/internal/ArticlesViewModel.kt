/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.articles.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesByLabelUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.feature.articles.ArticlesFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MSG_FAILED_LOAD = "Failed to load"
private const val MSG_REFRESH_FAILED = "Refresh failed"
private const val MSG_CONTENT_REFRESH_FAILED = "Content refresh failed"

/** Internal ViewModel exposing article collections and article content streams for the Articles feature. */
@HiltViewModel
internal class ArticlesViewModel @Inject constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getArticleContentUseCase: GetArticleContentUseCase,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
    private val getArticlesByLabelUseCase: GetArticlesByLabelUseCase,
) : ViewModel() {
    private val _articles = MutableStateFlow<Result<List<Article>>>(Result.Loading)
    val articles: StateFlow<Result<List<Article>>> = _articles.asStateFlow()

    private val _articleContent = MutableStateFlow<Result<ArticleContent>>(Result.Loading)
    val articleContent: StateFlow<Result<ArticleContent>> = _articleContent.asStateFlow()

    @Volatile private var started = false

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar

    data class ArticlesListUiState(
        val filter: ArticlesFilter = ArticlesFilter.All,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val items: List<Article> = emptyList(),
        val error: String? = null,
    )

    private val _listState = MutableStateFlow(ArticlesListUiState())
    val listState: StateFlow<ArticlesListUiState> = _listState.asStateFlow()

    fun ensureLoaded() {
        if (started) {
            return
        }
        started = true
        collectArticles()
        launchInitialBackgroundRefresh()
    }

    private fun collectArticles() = viewModelScope.launch {
        getArticlesUseCase().collect { result ->
            when (result) {
                is Result.Loading -> {
                    handleArticlesLoadingForList()
                }
                is Result.Success -> {
                    handleArticlesSuccessForList(result)
                }
                is Result.Error -> {
                    handleArticlesErrorForList(result)
                }
            }
        }
    }

    private fun handleArticlesLoadingForList() {
        handleArticlesLoading(Result.Loading)
        val ls = _listState.value
        if (ls.filter is ArticlesFilter.All && ls.items.isEmpty()) {
            _listState.value = ls.copy(loading = true, error = null)
        }
    }

    private fun handleArticlesSuccessForList(result: Result.Success<List<Article>>) {
        _articles.value = result
        if (_listState.value.filter is ArticlesFilter.All) {
            _listState.value = _listState.value.copy(
                loading = false,
                items = result.data,
                error = null,
            )
        }
    }

    private fun handleArticlesErrorForList(result: Result.Error) {
        handleArticlesError(result)
        if (_listState.value.filter is ArticlesFilter.All) {
            val listState = _listState.value
            val message = result.throwable.message ?: MSG_FAILED_LOAD
            if (listState.items.isNotEmpty()) {
                _snackbar.tryEmit(message)
                _listState.value = listState.copy(loading = false)
            } else {
                _listState.value = listState.copy(loading = false, error = message)
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

    private fun launchInitialBackgroundRefresh() = viewModelScope.launch {
        try {
            refreshArticlesUseCase()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // ignore other failures
        }
    }

    fun loadArticleContent(articleId: String) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId = articleId).collect { result ->
                processArticleContentResult(result)
            }
        }
    }

    private fun processArticleContentResult(result: Result<ArticleContent>) {
        when (result) {
            is Result.Loading -> {
                val current = _articleContent.value
                if (current !is Result.Success) {
                    _articleContent.value = result
                }
            }
            is Result.Success -> {
                _articleContent.value = result
            }
            is Result.Error -> {
                val current = _articleContent.value
                val hasBody = current is Result.Success && (
                    current.data.htmlBody.isNotBlank() ||
                        current.data.plainText.isNotBlank()
                    )
                if (hasBody) {
                    _snackbar.tryEmit(result.throwable.message ?: MSG_CONTENT_REFRESH_FAILED)
                } else {
                    _articleContent.value = result
                }
            }
        }
    }

    fun refreshArticles() {
        if (_refreshing.value) {
            return
        }
        _refreshing.value = true
        viewModelScope.launch {
            try {
                refreshArticlesUseCase()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                _refreshing.value = false
                throw ce
            } catch (_: Throwable) {
                // ignore
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refresh() = refreshArticles()

    fun loadList(filter: ArticlesFilter) {
        val current = _listState.value
        val alreadyLoaded = current.items.isNotEmpty() && !current.loading && !current.refreshing
        if (current.filter == filter && alreadyLoaded) {
            return
        }
        _listState.value = current.copy(
            filter = filter,
            loading = true,
            error = null,
            refreshing = false,
        )
        when (filter) {
            ArticlesFilter.All -> {
                ensureLoaded()
            }
            is ArticlesFilter.Label -> {
                fetchLabel(filter.value, isRefresh = false)
            }
        }
    }

    fun refreshList() {
        val current = _listState.value
        if (current.refreshing || current.loading) {
            return
        }
        val f = current.filter
        if (f is ArticlesFilter.All) {
            refreshArticles()
            return
        }
        if (f is ArticlesFilter.Label && f.value.isNotBlank()) {
            _listState.value = current.copy(refreshing = true, error = null)
            fetchLabel(f.value, isRefresh = true)
        }
    }

    private fun fetchLabel(label: String, isRefresh: Boolean) = viewModelScope.launch {
        try {
            val list = getArticlesByLabelUseCase(label)
            _listState.value = _listState.value.copy(
                loading = false,
                refreshing = false,
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
                    refreshing = false,
                )
            } else {
                _listState.value = _listState.value.copy(
                    loading = false,
                    refreshing = false,
                    items = emptyList(),
                    error = message,
                )
            }
        } finally {
            if (isRefresh) {
                _listState.value = _listState.value.copy(refreshing = false)
            }
        }
    }
}
