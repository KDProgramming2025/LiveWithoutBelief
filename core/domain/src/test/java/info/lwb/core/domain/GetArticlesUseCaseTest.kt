/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Article
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetArticlesUseCaseTest {
    private val mockRepository = mockk<ArticleRepository>()
    private val useCase = GetArticlesUseCase(mockRepository)

    @Test
    fun invokeReturnsArticlesFromRepository() = runTest {
        val expectedArticles = listOf(
            Article("1", "Title 1", "slug1", 1, "2025-01-01", 100),
            Article("2", "Title 2", "slug2", 1, "2025-01-02", 200),
        )
        coEvery { mockRepository.getArticles() } returns flowOf(Result.Success(expectedArticles))

        val result = useCase().first()
        assertEquals(Result.Success(expectedArticles), result)
    }

    @Test
    fun invokeReturnsErrorFromRepository() = runTest {
        val exception = Exception("Network error")
        coEvery { mockRepository.getArticles() } returns flowOf(Result.Error(exception))

        val result = useCase().first()
        assertEquals(Result.Error(exception), result)
    }
}
