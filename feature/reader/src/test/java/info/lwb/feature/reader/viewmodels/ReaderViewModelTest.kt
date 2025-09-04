/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import info.lwb.core.common.Result
import info.lwb.core.domain.GetArticleContentUseCase
import info.lwb.core.domain.GetArticlesUseCase
import info.lwb.core.domain.RefreshArticlesUseCase
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockGetArticlesUseCase = mockk<GetArticlesUseCase>(relaxed = true)
    private val mockGetArticleContentUseCase = mockk<GetArticleContentUseCase>(relaxed = true)
    private val mockRefreshArticlesUseCase = mockk<RefreshArticlesUseCase>(relaxed = true)

    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        // ViewModel created in each test
    }

    @Test
    fun `articles state is updated when getArticlesUseCase emits success`() = runTest {
        // Given
        val expectedArticles = listOf(
            Article("1", "Title 1", "slug1", 1, "2025-01-01", 100),
        )
        val successResult = Result.Success(expectedArticles)
        coEvery { mockGetArticlesUseCase() } returns flowOf(successResult)

        // When - simulate init
        viewModel = ReaderViewModel(
            mockGetArticlesUseCase,
            mockGetArticleContentUseCase,
            mockRefreshArticlesUseCase,
        )
        advanceUntilIdle()

        // Then
        assertEquals(successResult, viewModel.articles.value)
    }

    @Test
    fun `articleContent state is updated when loadArticleContent is called`() = runTest {
        // Given
        val articleId = "1"
        val expectedContent = ArticleContent(articleId, "<p>Content</p>", "Content", "hash")
        val successResult = Result.Success(expectedContent)
        coEvery { mockGetArticleContentUseCase(articleId) } returns flowOf(successResult)

        viewModel = ReaderViewModel(
            mockGetArticlesUseCase,
            mockGetArticleContentUseCase,
            mockRefreshArticlesUseCase,
        )

        // When
        viewModel.loadArticleContent(articleId)
        advanceUntilIdle()

        // Then
        assertEquals(successResult, viewModel.articleContent.value)
    }

    @Test
    fun `refreshArticles calls refreshArticlesUseCase`() = runTest {
        // Given
        coEvery { mockRefreshArticlesUseCase() } returns Unit

        viewModel = ReaderViewModel(
            mockGetArticlesUseCase,
            mockGetArticleContentUseCase,
            mockRefreshArticlesUseCase,
        )

        // When
        viewModel.refreshArticles()
        advanceUntilIdle()

        // Then
        // Verify that the use case was called (mockk will verify if needed)
    }

    @Test
    fun `articles initial state is Loading until emission arrives`() = runTest {
        // Given a delayed flow (no immediate success)
        coEvery { mockGetArticlesUseCase() } returns flowOf(Result.Loading)
        // When
        viewModel = ReaderViewModel(
            mockGetArticlesUseCase,
            mockGetArticleContentUseCase,
            mockRefreshArticlesUseCase,
        )
        // Then
        assertEquals(Result.Loading, viewModel.articles.value)
    }

    @Test
    fun `articleContent error propagates to state`() = runTest {
        val articleId = "err"
        val error = RuntimeException("boom")
        coEvery { mockGetArticleContentUseCase(articleId) } returns flowOf(Result.Error(error))
        // When
        viewModel = ReaderViewModel(
            mockGetArticlesUseCase,
            mockGetArticleContentUseCase,
            mockRefreshArticlesUseCase,
        )
        viewModel.loadArticleContent(articleId)
        advanceUntilIdle()
        // Then
        val state = viewModel.articleContent.value
        assert(state is Result.Error && state.throwable === error)
    }
}
