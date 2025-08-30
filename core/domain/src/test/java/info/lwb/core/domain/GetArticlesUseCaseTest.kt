package info.lwb.core.domain

import info.lwb.core.common.Result
import info.lwb.core.model.Article
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetArticlesUseCaseTest {

    private val mockRepository = mockk<ArticleRepository>()
    private val useCase = GetArticlesUseCase(mockRepository)

    @Test
    fun `invoke returns articles from repository`() = runTest {
        // Given
        val expectedArticles = listOf(
            Article("1", "Title 1", "slug1", 1, "2025-01-01", 100),
            Article("2", "Title 2", "slug2", 1, "2025-01-02", 200)
        )
        val successResult = Result.Success(expectedArticles)
        coEvery { mockRepository.getArticles() } returns flowOf(successResult)

        // When
        val result = useCase().first()

        // Then
        assertEquals(successResult, result)
    }

    @Test
    fun `invoke returns error from repository`() = runTest {
        // Given
        val exception = Exception("Network error")
        val errorResult = Result.Error(exception)
        coEvery { mockRepository.getArticles() } returns flowOf(errorResult)

        // When
        val result = useCase().first()

        // Then
        assertEquals(errorResult, result)
    }
}
