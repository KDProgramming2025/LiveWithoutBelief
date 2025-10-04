/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.lwb.feature.reader.ui.AppearanceState
// Use shared constants from ReaderViewModel.kt

@Preview(name = "Reader Light", showBackground = true)
@Composable
private fun previewReaderLight() {
    val sampleHtml =
        """
        <h1>Sample Title</h1>
        <p>Paragraph one with some text for preview.</p>
        <p>Paragraph two with more content to demonstrate scaling.</p>
        <img src='https://example.com/x.png' alt='x'/>
        """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Article",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(DEFAULT_FONT_SCALE, DEFAULT_LINE_HEIGHT, {}, {}),
        appearance = AppearanceState(
            fontScale = DEFAULT_FONT_SCALE,
            lineHeight = DEFAULT_LINE_HEIGHT,
            background = ReaderSettingsRepository.ReaderBackground.Paper,
            onFontScale = {},
            onLineHeight = {},
            onBackground = {},
        ),
    )
}

@Preview(name = "Reader Dark", showBackground = true)
@Composable
private fun previewReaderDark() {
    val sampleHtml =
        """
        <h1>Sample Title</h1>
        <p>Dark theme paragraph example.</p>
        <audio src='https://example.com/a.mp3'></audio>
        """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Dark",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(DEFAULT_FONT_SCALE, DEFAULT_LINE_HEIGHT, {}, {}),
        appearance = AppearanceState(
            fontScale = DEFAULT_FONT_SCALE,
            lineHeight = DEFAULT_LINE_HEIGHT,
            background = ReaderSettingsRepository.ReaderBackground.Night,
            onFontScale = {},
            onLineHeight = {},
            onBackground = {},
        ),
    )
}
