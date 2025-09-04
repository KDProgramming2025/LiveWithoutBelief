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
        paparazzi.snapshot {
            ReaderScreen()
        }
    }
}
