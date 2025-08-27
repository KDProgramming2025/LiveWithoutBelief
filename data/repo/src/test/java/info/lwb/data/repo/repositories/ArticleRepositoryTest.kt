package info.lwb.data.repo.repositories

import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeArticleDao : ArticleDao {
    override suspend fun getArticle(id: String): ArticleEntity? = null
    override suspend fun upsertArticle(article: ArticleEntity) { }
    override suspend fun upsertContent(content: ArticleContentEntity) { }
    override suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) { }
    override suspend fun getContent(articleId: String): ArticleContentEntity? = null
}

class ArticleRepositoryTest {

    @Test
    fun syncManifest_success_returnsList() = runTest {
        val api = object : ArticleApi {
            override suspend fun getManifest(): List<ManifestItemDto> = listOf(
                ManifestItemDto("1", "Title 1", "t1", 1, "2025-01-01T00:00:00Z", 100),
                ManifestItemDto("2", "Title 2", "t2", 1, "2025-01-02T00:00:00Z", 200)
            )
        }
        val repo = ArticleRepository(api, FakeArticleDao())
        val result = repo.syncManifest()
        assertEquals(2, result.size)
        assertEquals("Title 1", result[0].title)
    }

    @Test
    fun syncManifest_failure_returnsEmptyList() = runTest {
        val api = object : ArticleApi {
            override suspend fun getManifest(): List<ManifestItemDto> = error("boom")
        }
        val repo = ArticleRepository(api, FakeArticleDao())
        val result = repo.syncManifest()
        assertTrue(result.isEmpty())
    }
}

