/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme()

// Use a slightly lighter and cooler dark background while leaving surfaces at Material defaults.
// Hex constants to satisfy detekt's MagicNumber rule and align with design system style
private const val COLOR_DARK_BG = 0xFF262B33 // cooler blue‑gray, lighter than #121212
private val DarkBackground = Color(COLOR_DARK_BG.toInt())
private val DarkColors = darkColorScheme(
    background = DarkBackground,
)

/**
 * Top-level design system theme entry point for the application.
 *
 * Applies a Material3 [MaterialTheme] selecting either the dark or light color scheme based on
 * the provided [darkTheme] flag (defaults to system setting). Downstream composables receive
 * the themed [content] slot.
 *
 * @param darkTheme When true the dark color scheme is applied, otherwise the light scheme.
 * @param content Composable content rendered within the themed surface hierarchy.
 */
@Composable
fun LwbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
