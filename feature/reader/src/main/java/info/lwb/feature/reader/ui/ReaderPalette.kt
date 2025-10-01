/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import info.lwb.feature.reader.ReaderSettingsRepository
import java.io.BufferedReader
import java.io.InputStreamReader

/** Palette used for the article reading surface (distinct from global app theme). */
@Immutable
internal data class ReaderPalette(
    val name: String,
    val background: String,
    val text: String,
    val secondaryText: String,
    val divider: String,
)

/** Simple semantic accessor for palette (could be expanded later). */
internal val LocalReaderPalette =
    staticCompositionLocalOf { ReaderPalette("system", "#FFFFFF", "#111111", "#444444", "#DDDDDD") }

private val backgroundToPaletteMap: Map<ReaderSettingsRepository.ReaderBackground, ReaderPalette> = mapOf(
    ReaderSettingsRepository.ReaderBackground.Sepia to ReaderPalette(
        "sepia-light",
        "#F5ECD9",
        "#3A2F22",
        "#6E5A43",
        "#E6D7C0",
    ),
    ReaderSettingsRepository.ReaderBackground.Paper to ReaderPalette(
        "paper-light",
        "#FCFCF7",
        "#21211C",
        "#5E5E57",
        "#E8E8E0",
    ),
    ReaderSettingsRepository.ReaderBackground.Gray to ReaderPalette(
        "gray-light",
        "#F1F3F4",
        "#202124",
        "#5F6368",
        "#E0E2E3",
    ),
    ReaderSettingsRepository.ReaderBackground.Slate to ReaderPalette(
        "slate",
        "#2B2F36",
        "#E8EAED",
        "#B0B6BC",
        "#3A3F47",
    ),
    ReaderSettingsRepository.ReaderBackground.Charcoal to ReaderPalette(
        "charcoal",
        "#1F2328",
        "#E6E6E6",
        "#9EA7B3",
        "#2A2F35",
    ),
    ReaderSettingsRepository.ReaderBackground.Olive to ReaderPalette(
        "olive",
        "#3A3F2B",
        "#F1F3E6",
        "#C2C7B0",
        "#4A5038",
    ),
    ReaderSettingsRepository.ReaderBackground.Night to ReaderPalette(
        "night",
        "#000000",
        "#E6E6E6",
        "#9E9E9E",
        "#1A1A1A",
    ),
)

/** Map settings background + dark mode to a concrete palette. */
@Composable
internal fun readerPalette(bg: ReaderSettingsRepository.ReaderBackground): ReaderPalette =
    backgroundToPaletteMap.getValue(bg)

/** Resolve the asset path for the CSS file corresponding to this palette. */
internal fun themeCssAssetPath(palette: ReaderPalette): String = "webview/themes/${palette.name}.css"

/** Load a small text asset from this module's assets. */
internal fun loadAssetText(context: Context, path: String): String {
    context.assets.open(path).use { input ->
        return BufferedReader(InputStreamReader(input)).readText()
    }
}

/**
 * Merge inline HTML with injected CSS (appended inside <head>). If head is missing we create one.
 * This purposely avoids complex parsing; good enough for controlled HTML we own.
 */
internal fun mergeHtmlAndCss(html: String, css: String): String {
    if (css.isBlank()) {
        return html
    }
    val styleTag = "<style id=\"lwb-reader-theme\">\n$css\n</style>"
    val hasHead = html.contains("<head", ignoreCase = true)
    return if (hasHead) {
        // Inject before closing head (case-insensitive)
        val regex = Regex("</head>", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(html)) {
            html.replaceFirst(regex, styleTag + "\n</head>")
        } else {
            html + styleTag
        }
    } else {
        // Wrap entire body
        "<head>$styleTag</head>$html"
    }
}
