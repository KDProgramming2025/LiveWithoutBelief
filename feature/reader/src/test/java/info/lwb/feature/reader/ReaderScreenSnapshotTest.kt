/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import app.cash.paparazzi.Paparazzi
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore(
    "Paparazzi snapshot failing with IllegalAccessError under current AGP/Kotlin versions; pending version alignment",
)
class ReaderScreenSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun snapshot_readerPlaceholder() {
        val sampleHtml = """|<h1>Title</h1>
        |<p>This is a short paragraph for snapshot.</p>
        |<img src='https://example.com/img.png' alt='img'/>
        """.trimMargin()
        val settings = ReaderSettingsState(
            fontScale = 1.0,
            lineHeight = 1.2,
            onFontScaleChange = {},
            onLineHeightChange = {},
        )
        paparazzi.snapshot {
            ReaderScreen(
                articleTitle = "Snapshot",
                htmlBody = sampleHtml,
                settings = settings,
            )
        }
    }
}
