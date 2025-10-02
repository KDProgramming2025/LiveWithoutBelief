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

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

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
fun LwbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
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
