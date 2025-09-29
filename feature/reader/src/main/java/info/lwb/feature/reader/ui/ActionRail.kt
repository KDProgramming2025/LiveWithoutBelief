/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A floating action rail suitable for reading UIs.
 *
 * Behavior:
 * - Collapsed: shows only the main toggle button (defaults to a gear icon).
 * - Expanded: shows a vertical stack of pill-style actions (icon + label) above the main button,
 *   plus a light scrim that collapses the rail on outside tap.
 * - Items order: [items[0]] is closest to the main button (bottom-most), items grow upwards.
 */
@Composable
internal fun ActionRail(
    modifier: Modifier = Modifier,
    items: List<ActionRailItem>,
    mainIcon: ImageVector = Icons.Filled.Settings,
    mainContentDescription: String = "Reader actions",
    itemHeight: Dp = 48.dp,
    itemSpacing: Dp = 12.dp,
    railWidth: Dp = 160.dp,
    cornerRadius: Dp = 24.dp,
    edgePadding: Dp = 0.dp,
) {
    val view = LocalView.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Root box spans parent to allow scrim tap capture, but internal rail is width-constrained.
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim to collapse when expanded
        if (expanded) {
            val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { expanded = false },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = edgePadding, bottom = edgePadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Constrain the rail content to wrap its width to pills
            Column(horizontalAlignment = Alignment.End) {
                items.asReversed().forEachIndexed { indexFromTop, item ->
                    val indexFromBottom = items.lastIndex - indexFromTop
                    // Slight stagger per item (closer to main animates later)
                    val delay = 40 * indexFromBottom
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(90, delayMillis = delay)) +
                            expandHorizontally(tween(160 + delay, easing = FastOutSlowInEasing), Alignment.End) +
                            slideInVertically(tween(160 + delay)) { it / 5 },
                        exit = fadeOut(tween(80)) +
                            shrinkHorizontally(tween(120, easing = FastOutSlowInEasing), Alignment.End) +
                            slideOutVertically(tween(120)) { it / 5 },
                    ) {
                        ActionRailPill(
                            icon = item.icon,
                            label = item.label,
                            height = itemHeight,
                            width = railWidth,
                            cornerRadius = cornerRadius,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                expanded = false
                                item.onClick()
                            },
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(itemSpacing))
                    }
                }

                // Main toggle button (rectangular with rounded corners)
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(role = Role.Button) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            expanded = !expanded
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(mainIcon, contentDescription = mainContentDescription)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRailPill(
    icon: ImageVector,
    label: String,
    height: Dp,
    width: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(width)
            .heightIn(min = height)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(cornerRadius),
    ) {
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .heightIn(min = height)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** A single action item for [ActionRail]. items[0] is the bottom-most (closest to the main button). */
internal data class ActionRailItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)
