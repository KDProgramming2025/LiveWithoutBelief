/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.model.ArticleContent
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel dedicated to loading and refreshing a single article's content for the reader.
 * Decoupled from listing concerns which now reside in the Articles feature module.
 */
@HiltViewModel
internal class ReaderContentViewModel @Inject constructor(
    private val getArticleContentUseCase: GetArticleContentUseCase,
) : ViewModel() {
    private val _content = MutableStateFlow<Result<ArticleContent>>(Result.Loading)
    val content: StateFlow<Result<ArticleContent>> = _content.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var currentId: String? = null

    fun load(articleId: String) {
        val alreadyLoaded = currentId == articleId && _content.value is Result.Success
        if (alreadyLoaded) {
            return
        }
        currentId = articleId
        collect(articleId, isRefresh = false)
    }

    fun refresh() {
        val id = currentId ?: return
        if (_refreshing.value) {
            return
        }
        _refreshing.value = true
        collect(id, isRefresh = true)
    }

    private fun collect(articleId: String, isRefresh: Boolean) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        if (!isRefresh || _content.value !is Result.Success) {
                            _content.value = result
                        }
                    }
                    is Result.Success -> {
                        _content.value = result
                        _refreshing.value = false
                    }
                    is Result.Error -> {
                        _content.value = result
                        _refreshing.value = false
                    }
                }
            }
        }
    }
}
