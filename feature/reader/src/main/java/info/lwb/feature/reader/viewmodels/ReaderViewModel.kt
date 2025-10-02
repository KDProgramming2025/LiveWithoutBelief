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
 * ViewModel backing the Reader feature. It exposes a stream of article lists and the currently
 * requested article content. Data is provided via domain use cases which return [Result] wrapped
 * flows representing loading / success / error states.
 */
@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getArticleContentUseCase: GetArticleContentUseCase,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
) : ViewModel() {
    private val _articles = MutableStateFlow<Result<List<Article>>>(Result.Loading)

    /** Hot state flow of paged or filtered articles (loading, success or error). */
    val articles: StateFlow<Result<List<Article>>> = _articles.asStateFlow()

    private val _articleContent = MutableStateFlow<Result<ArticleContent>>(Result.Loading)

    /** Article content for the last requested article id (loading, success or error). */
    val articleContent: StateFlow<Result<ArticleContent>> = _articleContent.asStateFlow()

    init {
        loadArticles()
    }

    private fun loadArticles() {
        viewModelScope.launch {
            getArticlesUseCase().collect { result ->
                _articles.value = result
            }
        }
    }

    /**
     * Request the content for a specific article, updating [articleContent] as results arrive.
     */
    fun loadArticleContent(articleId: String) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId).collect { result ->
                _articleContent.value = result
            }
        }
    }

    /** Trigger a remote refresh of articles (e.g. user initiated pull-to-refresh). */
    fun refreshArticles() {
        viewModelScope.launch {
            refreshArticlesUseCase()
        }
    }
}
