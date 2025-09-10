/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.ArticleRepository
import info.lwb.core.model.Article
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val articleRepo: ArticleRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<Article>>(emptyList())
    val results: StateFlow<List<Article>> = _results

    fun onQueryChange(q: String) {
        _query.value = q
        viewModelScope.launch {
            _results.value = if (q.isBlank()) emptyList() else articleRepo.searchLocal(q, limit = 50)
        }
    }
}
