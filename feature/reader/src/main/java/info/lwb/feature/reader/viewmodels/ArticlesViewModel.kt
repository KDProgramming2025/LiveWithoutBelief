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
import kotlinx.coroutines.flow.MutableStateFlow
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

    init {
        startArticlesCollection()
    }

    private fun startArticlesCollection() {
        viewModelScope.launch {
            getArticlesUseCase().collect { result ->
                _articles.value = result
            }
        }
    }

    /** Requests content for [articleId] and emits progressive states to [articleContent]. */
    fun loadArticleContent(articleId: String) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId).collect { result ->
                _articleContent.value = result
            }
        }
    }

    /** Triggers a remote refresh of the article list (idempotent if backend unchanged). */
    fun refreshArticles() {
        viewModelScope.launch {
            refreshArticlesUseCase()
        }
    }
}
