/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import info.lwb.feature.reader.ReaderSettingsRepository

/**
 * Immutable snapshot of reader appearance settings and callbacks to mutate them.
 */
internal data class AppearanceState(
    val fontScale: Double,
    val lineHeight: Double,
    val background: ReaderSettingsRepository.ReaderBackground,
    val onFontScale: (Double) -> Unit,
    val onLineHeight: (Double) -> Unit,
    val onBackground: (ReaderSettingsRepository.ReaderBackground) -> Unit,
)
