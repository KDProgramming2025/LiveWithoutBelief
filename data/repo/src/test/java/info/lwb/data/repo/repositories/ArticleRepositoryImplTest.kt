/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import info.lwb.core.common.Result
import info.lwb.data.network.ArticleApi
import info.lwb.data.network.ArticleDto
import info.lwb.data.network.ManifestItemDto
import info.lwb.data.network.SectionDto
import info.lwb.data.repo.db.ArticleContentEntity
import info.lwb.data.repo.db.ArticleDao
import info.lwb.data.repo.db.ArticleEntity
import info.lwb.data.repo.db.ArticleSearchRow
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
    private val assets = mutableMapOf<String, MutableMap<String, info.lwb.data.repo.db.ArticleAssetEntity>>()
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
    override suspend fun upsertAssets(assets: List<info.lwb.data.repo.db.ArticleAssetEntity>) {
        assets.groupBy { it.articleId }.forEach { (aid, list) ->
            val bucket = this.assets.getOrPut(aid) { mutableMapOf() }
            list.forEach { a -> bucket[a.id] = a }
        }
    }
    override suspend fun pruneAssetsForArticle(articleId: String, keepIds: List<String>) {
        val bucket = assets[articleId] ?: return
        val keep = keepIds.toSet()
        val iter = bucket.keys.iterator()
        while (iter.hasNext()) {
            val id = iter.next()
            if (!keep.contains(id)) iter.remove()
        }
    }
    override suspend fun listAssets(articleId: String): List<info.lwb.data.repo.db.ArticleAssetEntity> =
        assets[articleId]?.values?.toList() ?: emptyList()
    override suspend fun deleteContentsNotIn(keepIds: List<String>) {
        val keep = keepIds.toSet()
        val iter = contents.keys.iterator()
        while (iter.hasNext()) {
            val k = iter.next()
            if (!keep.contains(k)) iter.remove()
        }
    }
    override suspend fun deleteAssetsNotIn(keepIds: List<String>) {
        val keep = keepIds.toSet()
        val articleIds = assets.keys.toList()
        for (aid in articleIds) {
            if (!keep.contains(aid)) {
                assets.remove(aid)
            }
        }
    }
    override suspend fun clearAllContents() {
        contents.clear()
    }
    override suspend fun clearAllAssets() {
        assets.clear()
    }
    override suspend fun searchArticlesLike(q: String, limit: Int, offset: Int): List<ArticleSearchRow> {
        // Simple in-memory LIKE: case-insensitive contains on title/plainText
        val lowered = q.lowercase()
        return articles.values
            .asSequence()
            .mapNotNull { a ->
                val c = contents[a.id] ?: return@mapNotNull null
                val match = a.title.lowercase().contains(lowered) || c.plainText.lowercase().contains(lowered)
                if (match) {
                    ArticleSearchRow(
                        id = a.id,
                        title = a.title,
                        slug = a.slug,
                        version = a.version,
                        updatedAt = a.updatedAt,
                        wordCount = a.wordCount,
                        plainText = c.plainText,
                    )
                } else {
                    null
                }
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
    override suspend fun getManifest(): info.lwb.data.network.ManifestResponse = info.lwb.data.network.ManifestResponse(
        manifest,
    )
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
        api.articles["m1"] =
            ArticleDto(
            id = "m1", slug = "m-slug-1", title = "M Title 1", version = 1, wordCount = 150,
            updatedAt = "2025-01-01T00:00:00Z", checksum = "", html = "<p>A</p>", text = "A",
            sections = listOf(SectionDto(0, "paragraph", text = "A")), media = emptyList(),
        )
        api.articles["m2"] =
            ArticleDto(
            id = "m2", slug = "m-slug-2", title = "M Title 2", version = 2, wordCount = 250,
            updatedAt = "2025-01-02T00:00:00Z", checksum = "", html = "<p>B</p>", text = "B",
            sections = listOf(SectionDto(0, "paragraph", text = "B")), media = emptyList(),
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
    fun refreshArticles_skipsDetailWhenVersionUnchangedAndHasContent() = runTest {
        // Existing article with version 3 and content
        dao.upsertArticle(ArticleEntity("x1", "Title X", "slug-x", 3, "2025-01-03", 100))
        dao.upsertContent(ArticleContentEntity("x1", "<p>old</p>", "old", "hash-old"))

        // Manifest has same version
        api.manifest = listOf(ManifestItemDto("x1", "Title X", "slug-x", 3, "2025-01-03", 100))
        // API should not be called for details if version unchanged and content exists; even if provided, repo should not overwrite
        api.articles["x1"] =
            ArticleDto(
            id = "x1", slug = "slug-x", title = "Title X", version = 3, wordCount = 100,
            updatedAt = "2025-01-03", checksum = "irrelevant", html = "<p>new</p>", text = "new",
            sections = emptyList(), media = emptyList(),
        )

        repo.refreshArticles()
        val c = dao.getArticleContent("x1")
        assertEquals("old", c?.plainText) // unchanged
    }

    @Test
    fun refreshArticles_dropsContentOnChecksumMismatch() = runTest {
        api.manifest = listOf(ManifestItemDto("c1", "C1", "c1", 1, "2025-01-01", 10))
        // Provide checksum that won't match sha256("hello")
        api.articles["c1"] =
            ArticleDto(
            id = "c1", slug = "c1", title = "C1", version = 1, wordCount = 10,
            updatedAt = "2025-01-01", checksum = "bad", html = "<p>hello</p>", text = "hello",
            sections = emptyList(), media = emptyList(),
        )
        repo.refreshArticles()
        // Content should not be saved due to checksum mismatch; article metadata still present
        val art = dao.getArticle("c1")
        val content = dao.getArticleContent("c1")
        assertEquals("C1", art?.title)
        assertEquals(null, content)
    }

    @Test
    fun refreshArticles_retriesOnFailureThenSucceeds() = runTest {
        // API fails first time for getArticle, succeeds next
        api.manifest = listOf(ManifestItemDto("r1", "R1", "r1", 1, "2025-01-01", 10))
        var attempts = 0
        val retryingApi = object : ArticleApi {
            override suspend fun getManifest(): info.lwb.data.network.ManifestResponse =
                info.lwb.data.network.ManifestResponse(
                    api.manifest,
                )
            override suspend fun getArticle(id: String): ArticleDto {
                attempts++
                if (attempts == 1) error("transient")
                return
                    ArticleDto(
                    id = id, slug = "r1", title = "R1", version = 1, wordCount = 10,
                    updatedAt = "2025-01-01",
                    // sha256("hello")
                    checksum = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    html = "<p>hello</p>", text = "hello", sections = emptyList(), media = emptyList(),
                )
            }
        }
        val retryingRepo = ArticleRepositoryImpl(api = retryingApi, articleDao = dao)

        retryingRepo.refreshArticles()
        val c = dao.getArticleContent("r1")
        assertEquals("hello", c?.plainText)
    }

    @Test
    fun refreshArticles_onNetworkError_insertsNothing() = runTest {
        // Simulate failure API
        val failingApi = object : ArticleApi {
            override suspend fun getManifest(): info.lwb.data.network.ManifestResponse = error("boom")
            override suspend fun getArticle(id: String): ArticleDto = error("not used")
        }
        val failingRepo = ArticleRepositoryImpl(api = failingApi, articleDao = dao)
        // When
        failingRepo.refreshArticles()
        // Then - no articles inserted
        assertEquals(0, dao.listArticles().size)
    }

    @Test
    fun refreshArticles_appliesEviction_keepsMostRecentN() = runTest {
        // Given 6 manifest items with increasing updatedAt
        api.manifest = (1..6).map { i ->
            ManifestItemDto("id$i", "T$i", "s$i", i, "2025-01-0${i}T00:00:00Z", i * 10)
        }
        // Provide article bodies so content gets stored
        (1..6).forEach { i ->
            api.articles["id$i"] =
                ArticleDto(
                id = "id$i", slug = "s$i", title = "T$i", version = i, wordCount = i * 10,
                updatedAt = "2025-01-0${i}T00:00:00Z", checksum = "", html = "<p>$i</p>", text = "$i",
                sections = emptyList(), media = emptyList(),
            )
        }
        // When
        repo.refreshArticles()
        // Then: eviction keeps most recent 4 contents
        val kept = (3..6).map { i -> dao.getArticleContent("id$i") != null }
        // id1 and id2 contents should be evicted
        val evicted = listOf(dao.getArticleContent("id1"), dao.getArticleContent("id2"))
        assertEquals(listOf(true, true, true, true), kept)
        assertEquals(listOf(null, null), evicted)
    }
}
