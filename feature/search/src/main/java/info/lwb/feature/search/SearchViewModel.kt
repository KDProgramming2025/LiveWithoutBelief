/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.SearchArticlesUseCase
import info.lwb.core.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the search feature coordinating the query text and asynchronous article results.
 * Uses [SearchArticlesUseCase] to perform a server/domain search, debouncing is intentionally omitted for
 * simplicity and immediate feedback (consider adding if backend load becomes a concern).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(private val searchArticles: SearchArticlesUseCase) : ViewModel() {
    private val _query = MutableStateFlow("")

    /** Current user-entered search text. */
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<Article>>(emptyList())

    /** Articles returned from the latest executed query. */
    val results: StateFlow<List<Article>> = _results

    /** Update the active search query and trigger a new search (or clear results if blank). */
    fun onQueryChange(q: String) {
        _query.value = q
        viewModelScope.launch {
            _results.value = if (q.isBlank()) {
                emptyList()
            } else {
                searchArticles(q, limit = 50)
            }
        }
    }
}
