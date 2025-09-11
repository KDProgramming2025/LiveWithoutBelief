package info.lwb.feature.reader

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReaderSettingsRepositoryTest {
    @Test
    fun defaults_and_updates() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Ensure clean DataStore file for deterministic test
        val dsDir = File(ctx.filesDir.parentFile, "datastore")
        if (dsDir.exists()) {
            dsDir.listFiles()?.filter { it.name.contains("reader_settings") }?.forEach { it.delete() }
        }
        val repo = ReaderSettingsRepository(ctx)
        assertEquals(1.0, repo.fontScale.first(), 0.0001)
        assertEquals(1.2, repo.lineHeight.first(), 0.0001)
        repo.setFontScale(1.4)
        repo.setLineHeight(1.8)
        assertEquals(1.4, repo.fontScale.first(), 0.0001)
        assertEquals(1.8, repo.lineHeight.first(), 0.0001)
    }
}
