/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.data.repo.repositories

import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ArticleDto
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.network.SectionDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import info.lwb.data.repo.db.ArticleSearchRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeDao : ArticleDao {
    val articles = mutableMapOf<String, ArticleEntity>()
    override suspend fun getArticle(id: String) = articles[id]
    override suspend fun listArticles() = articles.values.toList()
    override suspend fun upsertArticle(article: ArticleEntity) { articles[article.id] = article }
    override suspend fun upsertContent(content: ArticleContentEntity) {}
    override suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) {}
    override suspend fun getContent(articleId: String) = null
    override suspend fun getArticleContent(articleId: String) = null
    override suspend fun upsertAssets(assets: List<info.lwb.data.repo.db.ArticleAssetEntity>) {}
    override suspend fun pruneAssetsForArticle(articleId: String, keepIds: List<String>) {}
    override suspend fun listAssets(articleId: String) = emptyList<info.lwb.data.repo.db.ArticleAssetEntity>()
    override suspend fun deleteContentsNotIn(keepIds: List<String>) {}
    override suspend fun deleteAssetsNotIn(keepIds: List<String>) {}
    override suspend fun clearAllContents() {}
    override suspend fun clearAllAssets() {}
    override suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow> = emptyList()
}

private class StubApi : ArticleApi {
    var manifest: List<ManifestItemDto> = emptyList()
    override suspend fun getManifest(): info.lwb.data.network.ManifestResponse = info.lwb.data.network.ManifestResponse(manifest)
    override suspend fun getArticle(id: String): ArticleDto = error("not used")
}

class LabelArticleRepositoryImplTest {
    private lateinit var repo: LabelArticleRepositoryImpl
    private val dao = FakeDao()
    private val api = StubApi()

    @Before fun setup() { repo = LabelArticleRepositoryImpl(api, dao) }

    @Test
    fun filters_by_label_and_maps() = runTest {
        api.manifest = listOf(
            ManifestItemDto("1", "A", "a", 1, "2025-01-01", 10, label = "science"),
            ManifestItemDto("2", "B", "b", 1, "2025-01-02", 20, label = "ethics"),
            ManifestItemDto("3", "C", "c", 1, "2025-01-03", 30, label = "Science"),
        )
        val out = repo.listByLabel("science")
        assertEquals(listOf("1", "3"), out.map { it.id })
        // Upserted to DB
        assertEquals(2, dao.articles.size)
    }
}
