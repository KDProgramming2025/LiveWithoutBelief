package info.lwb.feature.reader.viewmodels

import app.cash.turbine.test
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused tests for [ArticlesViewModel]. Uses fake use cases to emit deterministic sequences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticlesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var articlesFlow: MutableSharedFlow<Result<List<Article>>>
    private lateinit var contentFlow: MutableSharedFlow<Result<ArticleContent>>

    private lateinit var getArticles: GetArticlesUseCase
    private lateinit var getArticleContent: GetArticleContentUseCase
    private lateinit var refreshArticles: RefreshArticlesUseCase

    @Before
    fun setUp() {
        articlesFlow = MutableSharedFlow(replay = 0)
        contentFlow = MutableSharedFlow(replay = 0)

        getArticles = GetArticlesUseCase { articlesFlow }
        getArticleContent = GetArticleContentUseCase { id: String ->
            @Suppress("UNUSED_PARAMETER")
            flow { contentFlow.collect { emit(it) } }
        }
        refreshArticles = RefreshArticlesUseCase { /* no-op */ }
    }

    @Test
    fun initialState_isLoading() = runTest(dispatcher) {
        val vm = ArticlesViewModel(getArticles, getArticleContent, refreshArticles)
        assertTrue(vm.articles.value is Result.Loading)
        assertTrue(vm.articleContent.value is Result.Loading)
    }

    @Test
    fun emitsArticlesAndContent() = runTest(dispatcher) {
        val vm = ArticlesViewModel(getArticles, getArticleContent, refreshArticles)
        val sampleArticles = listOf(Article(id = "a1", title = "T", slug = "s", summary = null))
        val sampleContent = ArticleContent(id = "a1", html = "<p>Hi</p>", indexUrl = "http://example/index.html")

        // Collect one emission sequence for articles
        vm.articles.test {
            articlesFlow.emit(Result.Success(sampleArticles))
            assertEquals(Result.Success(sampleArticles), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        vm.loadArticleContent("a1")
        vm.articleContent.test {
            contentFlow.emit(Result.Success(sampleContent))
            assertEquals(Result.Success(sampleContent), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
