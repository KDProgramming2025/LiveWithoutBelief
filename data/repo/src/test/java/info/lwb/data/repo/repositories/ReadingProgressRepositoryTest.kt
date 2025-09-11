package info.lwb.data.repo.repositories

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import info.lwb.data.repo.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressRepositoryTest {
    @Test
    fun updateAndObserve() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
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