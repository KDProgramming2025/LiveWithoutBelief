/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LwbThemeSnapshotTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test
    fun lightTheme_colorSwatch() {
        paparazzi.snapshot {
            LwbTheme(darkTheme = false) {
                ColorSwatch()
            }
        }
    }

    @Test
    fun darkTheme_colorSwatch() {
        paparazzi.snapshot {
            LwbTheme(darkTheme = true) {
                ColorSwatch()
            }
        }
    }
}
