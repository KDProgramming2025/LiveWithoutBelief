/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ArticleDto
import info.lwb.data.network.MediaDto
import info.lwb.data.network.SectionDto
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleSearchRow
import info.lwb.data.repo.db.ArticleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeArticleDao : ArticleDao {
    private val articles = mutableMapOf<String, ArticleEntity>()
    private val contents = mutableMapOf<String, ArticleContentEntity>()
    override suspend fun getArticle(id: String) = articles[id]
    override suspend fun listArticles() = articles.values.toList()
    override suspend fun upsertArticle(article: ArticleEntity) {
        articles[article.id] = article
    }
    override suspend fun upsertContent(content: ArticleContentEntity) {
        contents[content.articleId] = content
    }
    override suspend fun upsertArticleWithContent(article: ArticleEntity, content: ArticleContentEntity) {
        upsertArticle(article)
        upsertContent(content)
    }
    override suspend fun getContent(articleId: String) = contents[articleId]
    override suspend fun getArticleContent(articleId: String) = contents[articleId]
    override suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow> {
        // Simple in-memory LIKE: case-insensitive contains on title/plainText
        val lowered = q.lowercase()
        return articles.values
            .asSequence()
            .mapNotNull { a ->
                val c = contents[a.id] ?: return@mapNotNull null
                val match = a.title.lowercase().contains(lowered) || c.plainText.lowercase().contains(lowered)
                if (match) ArticleSearchRow(
                    id = a.id,
                    title = a.title,
                    slug = a.slug,
                    version = a.version,
                    updatedAt = a.updatedAt,
                    wordCount = a.wordCount,
                    plainText = c.plainText,
                ) else null
            }
            .sortedByDescending { it.updatedAt }
            .drop(offset)
            .take(limit)
            .toList()
    }
}

class StubArticleApi : ArticleApi {
    var manifest: List<ManifestItemDto> = emptyList()
    var articles: MutableMap<String, ArticleDto> = mutableMapOf()
    override suspend fun getManifest(): List<ManifestItemDto> = manifest
    override suspend fun getArticle(id: String): ArticleDto = articles[id] ?: error("not found")
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
        dao.upsertArticle(ArticleEntity("id1", "Title1", "slug1", 1, "now", 100))
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
        } else {
            error("Expected final Success, got $success with emissions=$emissions")
        }
    }

    @Test
    fun refreshArticles_syncsManifestItems() = runTest {
        // Given manifest with two items
        api.manifest = listOf(
            ManifestItemDto("m1", "M Title 1", "m-slug-1", 1, "2025-01-01T00:00:00Z", 150),
            ManifestItemDto("m2", "M Title 2", "m-slug-2", 2, "2025-01-02T00:00:00Z", 250),
        )
        api.articles["m1"] = ArticleDto(
            id = "m1", slug = "m-slug-1", title = "M Title 1", version = 1, wordCount = 150,
            updatedAt = "2025-01-01T00:00:00Z", checksum = "x", html = "<p>A</p>", text = "A",
            sections = listOf(SectionDto(0, "paragraph", text = "A")), media = emptyList()
        )
        api.articles["m2"] = ArticleDto(
            id = "m2", slug = "m-slug-2", title = "M Title 2", version = 2, wordCount = 250,
            updatedAt = "2025-01-02T00:00:00Z", checksum = "y", html = "<p>B</p>", text = "B",
            sections = listOf(SectionDto(0, "paragraph", text = "B")), media = emptyList()
        )
        // When
        repo.refreshArticles()
        // Then - articles should be inserted based on manifest (NOT stub)
        val listed = dao.listArticles()
        assertEquals(2, listed.size)
        assertEquals("M Title 1", listed.first { it.id == "m1" }.title)
        // And content hashed and saved
        val c1 = dao.getArticleContent("m1")
        val c2 = dao.getArticleContent("m2")
        assertEquals("A", c1?.plainText)
        assertEquals("B", c2?.plainText)
        check(!c1?.textHash.isNullOrBlank())
        check(!c2?.textHash.isNullOrBlank())
    }

    @Test
    fun refreshArticles_onNetworkError_insertsNothing() = runTest {
        // Simulate failure API
        val failingApi = object : ArticleApi {
            override suspend fun getManifest(): List<ManifestItemDto> = error("boom")
            override suspend fun getArticle(id: String): ArticleDto = error("not used")
        }
        val failingRepo = ArticleRepositoryImpl(api = failingApi, articleDao = dao)
        // When
        failingRepo.refreshArticles()
        // Then - no articles inserted
        assertEquals(0, dao.listArticles().size)
    }
}
