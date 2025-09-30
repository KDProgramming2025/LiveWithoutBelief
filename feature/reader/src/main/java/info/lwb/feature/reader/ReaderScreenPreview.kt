/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ReaderSettingsRepository

@Preview(name = "Reader Light", showBackground = true)
@Composable
private fun PreviewReaderLight() {
    val sampleHtml = """
        <h1>Sample Title</h1>
        <p>Paragraph one with some text for preview.</p>
        <p>Paragraph two with more content to demonstrate scaling.</p>
        <img src='https://example.com/x.png' alt='x'/>
    """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Article",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(1.0, 1.2, {}, {}),
        appearance = AppearanceState(
            fontScale = 1.0,
            lineHeight = 1.2,
            background = ReaderSettingsRepository.ReaderBackground.Paper,
            onFontScale = {},
            onLineHeight = {},
            onBackground = {},
        ),
    )
}

@Preview(name = "Reader Dark", showBackground = true)
@Composable
private fun PreviewReaderDark() {
    val sampleHtml = """
        <h1>Sample Title</h1>
        <p>Dark theme paragraph example.</p>
        <audio src='https://example.com/a.mp3'></audio>
    """.trimIndent()
    ReaderScreen(
        articleTitle = "Preview Dark",
        htmlBody = sampleHtml,
        settings = ReaderSettingsState(1.0, 1.2, {}, {}),
        appearance = AppearanceState(
            fontScale = 1.0,
            lineHeight = 1.2,
            background = ReaderSettingsRepository.ReaderBackground.Night,
            onFontScale = {},
            onLineHeight = {},
            onBackground = {},
        ),
    )
}
