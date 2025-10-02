/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import app.cash.turbine.test
import info.lwb.core.domain.ReadingProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeReadingProgressRepo : ReadingProgressRepository {
    private val flows = mutableMapOf<String, MutableStateFlow<ReadingProgressRepository.Progress?>>()

    override fun observe(articleId: String): StateFlow<ReadingProgressRepository.Progress?> =
        flows.getOrPut(articleId) { MutableStateFlow(null) }

    override suspend fun update(articleId: String, pageIndex: Int, totalPages: Int) {
        flows.getOrPut(articleId) { MutableStateFlow(null) }
            .value = ReadingProgressRepository.Progress(pageIndex, totalPages)
    }
}

private class FakeReaderSettingsRepository : ReaderSettingsRepositoryInterface {
    override val fontScale = MutableStateFlow(DEFAULT_FONT_SCALE)
    override val lineHeight = MutableStateFlow(DEFAULT_LINE_HEIGHT)
    override val background = MutableStateFlow(ReaderSettingsRepository.ReaderBackground.Paper)
    override suspend fun setFontScale(v: Double) { fontScale.value = v }
    override suspend fun setLineHeight(v: Double) { lineHeight.value = v }
    override suspend fun setBackground(bg: ReaderSettingsRepository.ReaderBackground) { background.value = bg }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var progress: FakeReadingProgressRepo
    private lateinit var settings: FakeReaderSettingsRepository

    @Before
    fun setUp() {
        progress = FakeReadingProgressRepo()
        settings = FakeReaderSettingsRepository()
    }

    @Test
    fun loadArticle_setsUiStateArticleId() = runTest(dispatcher) {
        val vm = ReaderSessionViewModel(progress, settings)
        vm.loadArticle("id1", "<h1>Title</h1><p>Body</p>")
        vm.uiState.test {
            val first = awaitItem()
            assertEquals("id1", first.articleId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onFontScaleChange_updatesState() = runTest(dispatcher) {
        val vm = ReaderSessionViewModel(progress, settings)
        vm.onFontScaleChange(1.25)
        assertEquals(1.25, vm.fontScale.value, 0.0)
    }
}
