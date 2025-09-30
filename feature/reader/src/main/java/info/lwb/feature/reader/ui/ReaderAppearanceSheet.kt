/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Region: constants extracted from previous magic numbers
private const val TONAL_ELEVATION_DP = 3
private const val FULL_WEIGHT = 1f

// Color hexes
private const val COLOR_SEPIA = 0xFFF5ECD9
private const val COLOR_PAPER = 0xFFFCFCF7
private const val COLOR_GRAY = 0xFFF1F3F4
private const val COLOR_SLATE = 0xFF2B2F36
private const val COLOR_CHARCOAL = 0xFF1F2328
private const val COLOR_OLIVE = 0xFF3A3F2B
private const val COLOR_NIGHT = 0xFF000000
private const val SCRIM_ANIM_DURATION_MS = 150
private const val SHEET_ENTER_DURATION_MS = 300
private const val SHEET_EXIT_DURATION_MS = 220
private const val SCRIM_ALPHA = 0.28f
private const val SHEET_CORNER_RADIUS_DP = 20
private const val SHEET_HORIZONTAL_PADDING_DP = 20
private const val SHEET_VERTICAL_PADDING_DP = 16
private const val SECTION_SPACER_SMALL_DP = 6
private const val SECTION_SPACER_MEDIUM_DP = 8
private const val SECTION_SPACER_LARGE_DP = 12
private const val FONT_OPTION_HEIGHT_DP = 36
private const val SWATCH_SIZE_DP = 32
private const val SWATCH_LABEL_GAP_DP = 4
private const val FONT_OPTION_CORNER_DP = 16

private const val FONT_SCALE_SMALLER = 0.9
private const val FONT_SCALE_BASE = 1.0
private const val FONT_SCALE_PLUS1 = 1.1
private const val FONT_SCALE_PLUS2 = 1.2
private const val FONT_SCALE_PLUS3 = 1.3
private val FONT_SCALE_OPTIONS = listOf(
    FONT_SCALE_SMALLER,
    FONT_SCALE_BASE,
    FONT_SCALE_PLUS1,
    FONT_SCALE_PLUS2,
    FONT_SCALE_PLUS3,
)

private const val LINE_HEIGHT_TIGHT = 1.2
private const val LINE_HEIGHT_NORMAL = 1.4
private const val LINE_HEIGHT_RELAXED = 1.6
private const val LINE_HEIGHT_LOOSE = 1.8
private val LINE_HEIGHT_OPTIONS = listOf(
    LINE_HEIGHT_TIGHT,
    LINE_HEIGHT_NORMAL,
    LINE_HEIGHT_RELAXED,
    LINE_HEIGHT_LOOSE,
)

private data class BackgroundOption(
    val name: String,
    val color: Color,
    val key: info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground,
)

private val BACKGROUND_OPTIONS = listOf(
    BackgroundOption(
        name = "Sepia",
        color = Color(COLOR_SEPIA),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Sepia,
    ),
    BackgroundOption(
        name = "Paper",
        color = Color(COLOR_PAPER),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Paper,
    ),
    BackgroundOption(
        name = "Gray",
        color = Color(COLOR_GRAY),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Gray,
    ),
    BackgroundOption(
        name = "Slate",
        color = Color(COLOR_SLATE),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Slate,
    ),
    BackgroundOption(
        name = "Charcoal",
        color = Color(COLOR_CHARCOAL),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Charcoal,
    ),
    BackgroundOption(
        name = "Olive",
        color = Color(COLOR_OLIVE),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Olive,
    ),
    BackgroundOption(
        name = "Night",
        color = Color(COLOR_NIGHT),
        key = info.lwb.feature.reader.ReaderSettingsRepository.ReaderBackground.Night,
    ),
)

@Composable
internal fun ReaderAppearanceSheet(
    visible: Boolean,
    state: AppearanceState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxSize()) {
        AppearanceSheetScrim(visible = visible, onDismiss = onDismiss)
        AppearanceSheetCard(visible = visible, modifier = modifier, state = state)
    }
}

@Composable
private fun AppearanceSheetScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(SCRIM_ANIM_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(SCRIM_ANIM_DURATION_MS)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable { onDismiss() },
        )
    }
}

@Composable
private fun AppearanceSheetCard(visible: Boolean, modifier: Modifier, state: AppearanceState) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(SHEET_ENTER_DURATION_MS)) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(SHEET_EXIT_DURATION_MS)) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Surface(
            tonalElevation = TONAL_ELEVATION_DP.dp,
            shape = RoundedCornerShape(topStart = SHEET_CORNER_RADIUS_DP.dp, topEnd = SHEET_CORNER_RADIUS_DP.dp),
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SHEET_HORIZONTAL_PADDING_DP.dp,
                        vertical = SHEET_VERTICAL_PADDING_DP.dp,
                    ),
            ) {
                Text("Reader appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(SECTION_SPACER_MEDIUM_DP.dp))
                FontScaleSection(state)
                Spacer(Modifier.height(SECTION_SPACER_LARGE_DP.dp))
                LineHeightSection(state)
                Spacer(Modifier.height(SECTION_SPACER_LARGE_DP.dp))
                BackgroundSection(state)
                Spacer(Modifier.height(SECTION_SPACER_MEDIUM_DP.dp))
            }
        }
    }
}

@Composable
private fun FontScaleSection(state: AppearanceState) {
    Text("Font size", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(SECTION_SPACER_SMALL_DP.dp))
    FontOptionRow(
        options = FONT_SCALE_OPTIONS,
        current = state.fontScale,
        onSelect = { state.onFontScale(it) },
        render = { v ->
            val label = when (v) {
                FONT_SCALE_SMALLER -> {
                    "A-"
                }
                FONT_SCALE_BASE -> {
                    "A"
                }
                FONT_SCALE_PLUS1 -> {
                    "A+"
                }
                FONT_SCALE_PLUS2 -> {
                    "A++"
                }
                else -> {
                    "A+++"
                }
            }
            Text(label)
        },
    )
}

@Composable
private fun LineHeightSection(state: AppearanceState) {
    Text("Line height", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(SECTION_SPACER_SMALL_DP.dp))
    FontOptionRow(
        options = LINE_HEIGHT_OPTIONS,
        current = state.lineHeight,
        onSelect = { state.onLineHeight(it) },
        render = { v ->
            val label = when (v) {
                LINE_HEIGHT_TIGHT -> {
                    "Tight"
                }
                LINE_HEIGHT_NORMAL -> {
                    "Normal"
                }
                LINE_HEIGHT_RELAXED -> {
                    "Relaxed"
                }
                else -> {
                    "Loose"
                }
            }
            Text(label)
        },
    )
}

@Composable
private fun BackgroundSection(state: AppearanceState) {
    Text("Background", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(SECTION_SPACER_MEDIUM_DP.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(SECTION_SPACER_LARGE_DP.dp)) {
        BACKGROUND_OPTIONS.forEach { opt ->
            BackgroundSwatch(
                name = opt.name,
                color = opt.color,
                selected = state.background == opt.key,
            ) {
                state.onBackground(opt.key)
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(SWATCH_SIZE_DP.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(SWATCH_LABEL_GAP_DP.dp))
        val style = if (selected) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.labelSmall
        }
        Text(name, style = style)
    }
}

@Composable
private fun <T : Comparable<T>> FontOptionRow(
    options: List<T>,
    current: T,
    onSelect: (T) -> Unit,
    render: @Composable (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACER_MEDIUM_DP.dp),
    ) {
        options.forEach { v ->
            val selected = v == current
            val elevation = if (selected) {
                3.dp
            } else {
                0.dp
            }
            val borderStroke = if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            }
            Surface(
                tonalElevation = elevation,
                shape = RoundedCornerShape(FONT_OPTION_CORNER_DP.dp),
                border = borderStroke,
                modifier = Modifier
                    .weight(FULL_WEIGHT)
                    .height(FONT_OPTION_HEIGHT_DP.dp)
                    .clickable { onSelect(v) },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    render(v)
                }
            }
        }
    }
}
