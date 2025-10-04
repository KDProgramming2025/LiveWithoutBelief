/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel exposing article collections and individual article content streams.
 * Pure acquisition layer – no pagination or appearance concerns.
 */
@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getArticleContentUseCase: GetArticleContentUseCase,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
) : ViewModel() {
    private val _articles = MutableStateFlow<Result<List<Article>>>(Result.Loading)

    /** Latest article list with loading / success / error states. */
    val articles: StateFlow<Result<List<Article>>> = _articles.asStateFlow()

    private val _articleContent = MutableStateFlow<Result<ArticleContent>>(Result.Loading)

    /** Content result for the most recently requested article id. */
    val articleContent: StateFlow<Result<ArticleContent>> = _articleContent.asStateFlow()

    @Volatile private var started = false

    private val _refreshing = MutableStateFlow(false)

    /** Exposed refreshing flag to drive pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Transient notifications (snackbar text) for recoverable refresh/content failures occurring
     * while previously loaded data remains visible.
     */
    val snackbar: SharedFlow<String> = _snackbar

    /** Idempotent starter: begins collecting articles flow and launches one background refresh. */
    fun ensureLoaded() {
        if (started) {
            return
        }
        started = true
        collectArticles()
        launchInitialBackgroundRefresh()
    }

    private fun collectArticles() = viewModelScope.launch {
        getArticlesUseCase().collect { result: Result<List<Article>> ->
            when (result) {
                is Result.Loading -> {
                    handleArticlesLoading(result)
                }
                is Result.Success -> {
                    _articles.value = result
                }
                is Result.Error -> {
                    handleArticlesError(result)
                }
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
            _snackbar.tryEmit(result.throwable.message ?: "Refresh failed")
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
            // Initial background refresh failure will surface through articles flow; safe to ignore here.
        }
    }

    /** Requests content for [articleId] and emits progressive states to [articleContent]. */
    fun loadArticleContent(articleId: String) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId = articleId).collect { result: Result<ArticleContent> ->
                processArticleContentResult(result = result)
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
                val hasBody = current is Result.Success &&
                    (current.data.htmlBody.isNotBlank() || current.data.plainText.isNotBlank())
                if (hasBody) {
                    _snackbar.tryEmit(result.throwable.message ?: "Content refresh failed")
                } else {
                    _articleContent.value = result
                }
            }
        }
    }

    /** Triggers a remote refresh of the article list (idempotent if backend unchanged). */
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
                // Error surfaced through upstream flows or snackbar; swallow after state reset.
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Alias for UI pull-to-refresh semantics; always attempts refresh. */
    fun refresh() = refreshArticles()
}
