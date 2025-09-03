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
    private val articles = mutableListOf<ArticleEntity>()
    private val contents = mutableMapOf<String, ArticleContentEntity>()
    override suspend fun getArticle(id: String): ArticleEntity? = articles.firstOrNull { it.id == id }
    override suspend fun listArticles(): List<ArticleEntity> = articles.toList()
    override suspend fun upsertArticle(article: ArticleEntity) { articles.removeAll { it.id == article.id }; articles.add(article) }
    override suspend fun upsertContent(content: ArticleContentEntity) { contents[content.articleId] = content }
    override suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) { upsertArticle(article); upsertContent(content) }
    override suspend fun getContent(articleId: String): ArticleContentEntity? = contents[articleId]
    override suspend fun getArticleContent(articleId: String): ArticleContentEntity? = contents[articleId]
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
    val repo = ArticleRepositoryImpl(api, FakeArticleDao())
        val result = repo.syncManifest()
        assertEquals(2, result.size)
        assertEquals("Title 1", result[0].title)
    }

    @Test
    fun syncManifest_failure_returnsEmptyList() = runTest {
        val api = object : ArticleApi {
            override suspend fun getManifest(): List<ManifestItemDto> = error("boom")
        }
    val repo = ArticleRepositoryImpl(api, FakeArticleDao())
        val result = repo.syncManifest()
        assertTrue(result.isEmpty())
    }
}

