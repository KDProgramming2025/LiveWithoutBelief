/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import info.lwb.feature.reader.ReaderSettingsRepository
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/** Palette used for the article reading surface (distinct from global app theme). */
@Immutable
data class ReaderPalette(
    val name: String,
    val background: String,
    val text: String,
    val secondaryText: String,
    val divider: String,
)

/** Simple semantic accessor for palette (could be expanded later). */
val LocalReaderPalette = staticCompositionLocalOf { ReaderPalette("system", "#FFFFFF", "#111111", "#444444", "#DDDDDD") }

/** Map settings background + dark mode to a concrete palette. */
@Composable
fun readerPalette(bg: ReaderSettingsRepository.ReaderBackground): ReaderPalette {
    val dark = isSystemInDarkTheme()
    return when (bg) {
        ReaderSettingsRepository.ReaderBackground.System -> if (dark) {
            ReaderPalette("system-dark", "#121212", "#FAFAFA", "#BBBBBB", "#222222")
        } else {
            ReaderPalette("system-light", "#FFFFFF", "#111111", "#555555", "#EEEEEE")
        }
        ReaderSettingsRepository.ReaderBackground.Sepia -> if (dark) {
            ReaderPalette("sepia-dark", "#2E261C", "#FAE8C8", "#B79C74", "#3A3024")
        } else {
            ReaderPalette("sepia-light", "#F5ECD9", "#3A2F22", "#6E5A43", "#E6D7C0")
        }
        ReaderSettingsRepository.ReaderBackground.Paper -> if (dark) {
            ReaderPalette("paper-dark", "#1E1E1A", "#EEEDE8", "#B5B5AE", "#2A2A26")
        } else {
            ReaderPalette("paper-light", "#FCFCF7", "#21211C", "#5E5E57", "#E8E8E0")
        }
        ReaderSettingsRepository.ReaderBackground.Gray -> if (dark) {
            ReaderPalette("gray-dark", "#202124", "#F1F3F4", "#B8B9BA", "#2C2D30")
        } else {
            ReaderPalette("gray-light", "#F1F3F4", "#202124", "#5F6368", "#E0E2E3")
        }
        ReaderSettingsRepository.ReaderBackground.Night -> ReaderPalette("night", "#000000", "#E6E6E6", "#9E9E9E", "#1A1A1A")
    }
}

/** Resolve the asset path for the CSS file corresponding to this palette. */
fun themeCssAssetPath(palette: ReaderPalette): String = "webview/themes/${palette.name}.css"

/** Load a small text asset from this module's assets. */
fun loadAssetText(context: Context, path: String): String {
    context.assets.open(path).use { input ->
        return BufferedReader(InputStreamReader(input)).readText()
    }
}

/**
 * Merge inline HTML with injected CSS (appended inside <head>). If head is missing we create one.
 * This purposely avoids complex parsing; good enough for controlled HTML we own.
 */
fun mergeHtmlAndCss(html: String, css: String): String {
    if (css.isBlank()) return html
    val styleTag = "<style id=\"lwb-reader-theme\">\n$css\n</style>"
    val hasHead = html.contains("<head", ignoreCase = true)
    return if (hasHead) {
        // Inject before closing head (case-insensitive)
        val regex = Regex("</head>", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(html)) html.replaceFirst(regex, styleTag + "\n</head>") else html + styleTag
    } else {
        // Wrap entire body
        "<head>$styleTag</head>$html"
    }
}
