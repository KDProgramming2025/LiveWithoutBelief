package info.lwb.data.repo.repositories

import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ManifestItemDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import info.lwb.core.common.Result
import org.junit.Before
import org.junit.Test

class FakeArticleDao : ArticleDao {
    private val articles = mutableMapOf<String, ArticleEntity>()
    private val contents = mutableMapOf<String, ArticleContentEntity>()
    override suspend fun getArticle(id: String) = articles[id]
    override suspend fun listArticles() = articles.values.toList()
    override suspend fun upsertArticle(article: ArticleEntity) { articles[article.id] = article }
    override suspend fun upsertContent(content: ArticleContentEntity) { contents[content.articleId] = content }
    override suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) { upsertArticle(article); upsertContent(content) }
    override suspend fun getContent(articleId: String) = contents[articleId]
    override suspend fun getArticleContent(articleId: String) = contents[articleId]
}

class StubArticleApi : ArticleApi {
    var manifest: List<ManifestItemDto> = emptyList()
    override suspend fun getManifest(): List<ManifestItemDto> = manifest
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleRepositoryImplTest {
    private lateinit var repo: ArticleRepositoryImpl
    private val dao = FakeArticleDao()
    private val api = StubArticleApi()

    @Before
    fun setup() {
        // simple repo that currently relies only on DAO for tests
    repo = ArticleRepositoryImpl(api = api, articleDao = dao)
    }

    @Test
    fun emitsLocalArticles() = runTest {
        dao.upsertArticle(ArticleEntity("id1","Title1","slug1",1,"now",100))
        val emissions = mutableListOf<Result<*>>()
        repo.getArticles().collect { value ->
            emissions.add(value)
            if (value is Result.Success<*>) return@collect
        }
        // Expect at least Loading then Success
        val success = emissions.last()
        if (success is Result.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            val list = success.data as List<*>
            assertEquals(1, list.size)
        } else error("Expected final Success, got $success with emissions=$emissions")
    }
}
