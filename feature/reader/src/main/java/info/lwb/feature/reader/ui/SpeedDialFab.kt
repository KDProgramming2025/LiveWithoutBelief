/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

data class SpeedDialItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
fun SpeedDialFab(
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier,
    mainIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Settings,
    onMainClick: (() -> Unit)? = null,
    expandedRadius: Dp = 88.dp,
    miniFabSize: Dp = 36.dp,
) {
    // Manage expansion state locally so container only controls visibility
    var expanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val stepRadiusPx = with(density) { expandedRadius.toPx() } // used as vertical step between minis

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        // Main FAB (force circular and draw first so children appear above)
        FloatingActionButton(
            onClick = {
                // Show/keep visible controlled by container; we just toggle expansion
                expanded = !expanded
                onMainClick?.invoke()
            },
            modifier = Modifier.clip(CircleShape),
            shape = CircleShape,
        ) {
            Icon(mainIcon, contentDescription = "Reader settings")
        }

        // Children actions: stack vertically above the main FAB.
        val n = items.size
        if (n > 0) {
            items.forEachIndexed { i, item ->
                // Index 0 is the bottom-most mini (closest to main), then up.
                val targetDx = 0f
                val targetDy = -(stepRadiusPx * (i + 1)).toFloat()
                androidx.compose.runtime.key(item.contentDescription) {
                    // Explicit progress animation per item with small stagger
                    val progress = remember { Animatable(0f) }
                    LaunchedEffect(expanded) {
                        if (expanded) {
                            kotlinx.coroutines.delay((i * 20).toLong())
                            progress.animateTo(1f, tween(durationMillis = 220))
                        } else {
                            progress.animateTo(0f, tween(durationMillis = 180))
                        }
                    }
                    val p = progress.value
                    val dx = targetDx * p
                    val dy = targetDy * p
                    val sizeDp = miniFabSize * (0.001f + 0.999f * p)
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = dx
                                translationY = dy
                                alpha = p
                                val s = 0.8f + 0.2f * p
                                scaleX = s
                                scaleY = s
                            }
                            .size(sizeDp)
                            .zIndex(2f),
                        shape = CircleShape,
                    ) {
                        Icon(item.icon, contentDescription = item.contentDescription)
                    }
                }
            }
        }
    }
}
