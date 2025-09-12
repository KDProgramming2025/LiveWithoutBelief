/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ReaderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test fun header_snapshot() {
        paparazzi.snapshot { headerSample("Live Without Belief") }
    }
}

@Composable
private fun headerSample(title: String) {
    MaterialTheme { Text(text = title) }
}
