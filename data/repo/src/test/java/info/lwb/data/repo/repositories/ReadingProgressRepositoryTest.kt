/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import info.lwb.data.repo.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressRepositoryTest {
    @Test
    fun updateAndObserve() = runTest {
        val ctxResult = runCatching { ApplicationProvider.getApplicationContext<android.content.Context>() }
        if (ctxResult.isFailure) {
            // Skip when application context not available in this environment
            assumeTrue("Skipping: Application context unavailable", false)
        }
        val ctx = ctxResult.getOrThrow()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val repo = ReadingProgressRepository(db.readingProgressDao())
        val articleId = "a1"
        repo.update(articleId, pageIndex = 2, totalPages = 5)
        val p = repo.observe(articleId).first()!!
        assertEquals(2, p.pageIndex)
        assertEquals(5, p.totalPages)
        assertEquals(0.5, p.progress, 0.0001)
    }
}
