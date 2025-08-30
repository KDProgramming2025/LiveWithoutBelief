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

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getArticleContentUseCase: GetArticleContentUseCase,
    private val refreshArticlesUseCase: RefreshArticlesUseCase,
) : ViewModel() {

    private val _articles = MutableStateFlow<Result<List<Article>>>(Result.Loading)
    val articles: StateFlow<Result<List<Article>>> = _articles.asStateFlow()

    private val _articleContent = MutableStateFlow<Result<ArticleContent>>(Result.Loading)
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

    fun loadArticleContent(articleId: String) {
        viewModelScope.launch {
            getArticleContentUseCase(articleId).collect { result ->
                _articleContent.value = result
            }
        }
    }

    fun refreshArticles() {
        viewModelScope.launch {
            refreshArticlesUseCase()
        }
    }
}
