/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import java.io.File

class ReaderSettingsRepositoryTest {
    @Test
    fun defaults_and_updates() = runTest {
        val ctx = try {
            ApplicationProvider.getApplicationContext<android.content.Context>()
        } catch (e: IllegalStateException) {
            Assume.assumeTrue("Skipping: Application context unavailable", false)
            throw e
        }
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
