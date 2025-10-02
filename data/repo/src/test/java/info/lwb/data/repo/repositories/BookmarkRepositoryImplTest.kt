/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import androidx.room.Room
import info.lwb.core.domain.UserSession
import info.lwb.data.repo.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BookmarkRepositoryImplTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private val db = Room
        .inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val session = object : UserSession {
        override fun currentUserId(): String? = "u1"
    }
    private val repo = BookmarkRepositoryImpl(db.bookmarkDao(), db.folderDao(), session)

    @Test
    fun addAndRemoveBookmark_emits() = runBlocking {
        val flow = repo.getBookmarks()
        // Initially empty
        val empty = (flow.first() as info.lwb.core.common.Result.Success).data
        assertEquals(0, empty.size)
        repo.addBookmark("a1", null)
        val one = (flow.first() as info.lwb.core.common.Result.Success).data
        assertEquals(1, one.size)
        repo.removeBookmark("u1:a1")
        val back = (flow.first() as info.lwb.core.common.Result.Success).data
        assertEquals(0, back.size)
    }

    @Test
    fun createFolder_and_moveBookmark_and_search() = runBlocking {
        // Seed two articles
        val articleDao = db.articleDao()
        articleDao.upsertArticle(
            info.lwb.data.repo.db.ArticleEntity(
                id = "a1",
                title = "Hello Kotlin",
                slug = "hello-kotlin",
                version = 1,
                updatedAt = "2024-01-01",
                wordCount = 1000,
            ),
        )
        articleDao.upsertContent(
            info.lwb.data.repo.db.ArticleContentEntity(
                articleId = "a1",
                htmlBody = "<p>Hello Kotlin</p>",
                plainText = "Hello Kotlin body",
                textHash = "t1",
            ),
        )
        articleDao.upsertArticle(
            info.lwb.data.repo.db.ArticleEntity(
                id = "a2",
                title = "World Compose",
                slug = "world-compose",
                version = 1,
                updatedAt = "2024-01-02",
                wordCount = 900,
            ),
        )
        articleDao.upsertContent(
            info.lwb.data.repo.db.ArticleContentEntity(
                articleId = "a2",
                htmlBody = "<p>World Compose</p>",
                plainText = "World Compose body",
                textHash = "t2",
            ),
        )

        // Add bookmark for a1
        repo.addBookmark("a1", null)

        // Create folder (idempotent)
        val f1 = (repo.createFolder("Read Later") as info.lwb.core.common.Result.Success).data
        val f1Again = (repo.createFolder("Read Later") as info.lwb.core.common.Result.Success).data
        assertEquals(f1, f1Again)

        // Move bookmark into folder
        (repo.moveBookmark("u1:a1", f1) as info.lwb.core.common.Result.Success)
        val afterMove = (repo.getBookmarks().first() as info.lwb.core.common.Result.Success).data
        assertEquals(f1, afterMove.first().folderId)

        // Search bookmarked should return only a1 for query 'Hello'
        val results = repo.searchBookmarked("Hello", 10, 0)
        assertEquals(1, results.size)
        assertEquals("a1", results.first().id)
    }
}
