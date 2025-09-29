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
import info.lwb.feature.reader.ReaderSettingsRepository

internal data class AppearanceState(
    val fontScale: Double,
    val lineHeight: Double,
    val background: ReaderSettingsRepository.ReaderBackground,
    val onFontScale: (Double) -> Unit,
    val onLineHeight: (Double) -> Unit,
    val onBackground: (ReaderSettingsRepository.ReaderBackground) -> Unit,
)

@Composable
internal fun ReaderAppearanceSheet(
    visible: Boolean,
    state: AppearanceState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable { onDismiss() },
            )
        }

        // Bottom card
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Reader appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Font size", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    FontOptionRow(
                        options = listOf(0.9, 1.0, 1.1, 1.2, 1.3),
                        current = state.fontScale,
                        onSelect = { state.onFontScale(it) },
                        render = { v ->
                            val label = when (v) {
                                0.9 -> {
                                    "A-"
                                }
                                1.0 -> {
                                    "A"
                                }
                                1.1 -> {
                                    "A+"
                                }
                                1.2 -> {
                                    "A++"
                                }
                                else -> {
                                    "A+++"
                                }
                            }
                            Text(label)
                        },
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Line height", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    FontOptionRow(
                        options = listOf(1.2, 1.4, 1.6, 1.8),
                        current = state.lineHeight,
                        onSelect = { state.onLineHeight(it) },
                        render = { v ->
                            val label = when (v) {
                                1.2 -> {
                                    "Tight"
                                }
                                1.4 -> {
                                    "Normal"
                                }
                                1.6 -> {
                                    "Relaxed"
                                }
                                else -> {
                                    "Loose"
                                }
                            }
                            Text(label)
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Background", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BackgroundSwatch(
                            name = "Sepia",
                            color = Color(0xFFF5ECD9),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Sepia,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Sepia) }
                        BackgroundSwatch(
                            name = "Paper",
                            color = Color(0xFFFCFCF7),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Paper,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Paper) }
                        BackgroundSwatch(
                            name = "Gray",
                            color = Color(0xFFF1F3F4),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Gray,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Gray) }
                        BackgroundSwatch(
                            name = "Slate",
                            color = Color(0xFF2B2F36),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Slate,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Slate) }
                        BackgroundSwatch(
                            name = "Charcoal",
                            color = Color(0xFF1F2328),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Charcoal,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Charcoal) }
                        BackgroundSwatch(
                            name = "Olive",
                            color = Color(0xFF3A3F2B),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Olive,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Olive) }
                        BackgroundSwatch(
                            name = "Night",
                            color = Color(0xFF000000),
                            selected = state.background == ReaderSettingsRepository.ReaderBackground.Night,
                        ) { state.onBackground(ReaderSettingsRepository.ReaderBackground.Night) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        val style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { v ->
            val selected = v == current
            Surface(
                tonalElevation = if (selected) 3.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clickable { onSelect(v) },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    render(v)
                }
            }
        }
    }
}
