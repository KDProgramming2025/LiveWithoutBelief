package info.lwb.data.repo.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.articleDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun upsertAndFetchArticleAndContent() = runTest {
        val article = ArticleEntity(id = "a1", title = "Title", slug = "slug", version = 1, updatedAt = "now", wordCount = 100)
        val content = ArticleContentEntity(articleId = "a1", content = "Body")
        dao.upsertArticleWithContent(article, content)
        val storedArticle = dao.getArticle("a1")
        val storedContent = dao.getArticleContent("a1")
        assertNotNull(storedArticle)
        assertEquals("Body", storedContent?.content)
    }
}
