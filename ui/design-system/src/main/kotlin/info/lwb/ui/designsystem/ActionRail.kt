/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.ui.designsystem

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * A floating, expandable vertical action rail for contextual quick actions.
 *
 * Usage scenarios:
 *  - Reader screen: per‑page or selection actions.
 *  - Home/dashboard: quick creation or navigation actions.
 *
 * UX details:
 *  - Collapsed: Only the toggle button is visible.
 *  - Expanded: Actions appear in a vertical stack (first item in [items] nearest the toggle).
 *  - Tapping outside (scrim) or selecting an action collapses the rail.
 *  - Internal state keeps API minimal; can be lifted later if external control is needed.
 */
@Composable
fun ActionRail(
    items: List<ActionRailItem>,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector,
    mainContentDescription: String,
    itemHeight: Dp = DefaultDimensions.ItemHeight,
    itemSpacing: Dp = DefaultDimensions.ItemSpacing,
    railWidth: Dp = DefaultDimensions.RailWidth,
    cornerRadius: Dp = DefaultDimensions.CornerRadius,
    edgePadding: Dp = 0.dp,
) {
    val view = LocalView.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        RailScrim(expanded = expanded) { expanded = false }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = edgePadding, bottom = edgePadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                RailItems(
                    expanded = expanded,
                    items = items,
                    itemHeight = itemHeight,
                    itemSpacing = itemSpacing,
                    railWidth = railWidth,
                    cornerRadius = cornerRadius,
                    onItemClick = { item ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expanded = false
                        item.onClick()
                    },
                )
                RailToggleButton(
                    onToggle = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expanded = !expanded
                    },
                    mainIcon = mainIcon,
                    mainContentDescription = mainContentDescription,
                )
            }
        }
    }
}

/**
 * Describes a single actionable pill displayed in [ActionRail].
 * @property icon Vector icon rendered at start of the pill.
 * @property label Short human readable label (single line, ellipsized if overflow).
 * @property onClick Callback executed after haptic feedback when the pill is tapped.
 */
data class ActionRailItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun RailScrim(expanded: Boolean, onCollapse: () -> Unit) {
    if (expanded) {
        val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
        Box(
            Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onCollapse() },
        )
    }
}

@Composable
private fun RailItems(
    expanded: Boolean,
    items: List<ActionRailItem>,
    itemHeight: Dp,
    itemSpacing: Dp,
    railWidth: Dp,
    cornerRadius: Dp,
    onItemClick: (ActionRailItem) -> Unit,
) {
    items.asReversed().forEachIndexed { indexFromTop, item ->
        val indexFromBottom = items.lastIndex - indexFromTop
        val delay = ITEM_STAGGER_BASE_MS * indexFromBottom
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(FADE_IN_DURATION_MS, delayMillis = delay)) +
                expandHorizontally(tween(EXPAND_DURATION_MS + delay, easing = FastOutSlowInEasing), Alignment.End) +
                slideInVertically(tween(EXPAND_DURATION_MS + delay)) { it / SLIDE_DIVISOR },
            exit = fadeOut(tween(FADE_OUT_DURATION_MS)) +
                shrinkHorizontally(tween(SHRINK_DURATION_MS, easing = FastOutSlowInEasing), Alignment.End) +
                slideOutVertically(tween(SHRINK_DURATION_MS)) { it / SLIDE_DIVISOR },
        ) {
            ActionRailPill(
                icon = item.icon,
                label = item.label,
                height = itemHeight,
                width = railWidth,
                cornerRadius = cornerRadius,
                onClick = { onItemClick(item) },
            )
        }
        if (expanded) {
            Spacer(Modifier.height(itemSpacing))
        }
    }
}

@Composable
private fun RailToggleButton(
    onToggle: () -> Unit,
    mainIcon: ImageVector,
    mainContentDescription: String,
) {
    Surface(
        modifier = Modifier
            .size(TOGGLE_BUTTON_SIZE)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button) { onToggle() },
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = TOGGLE_SHADOW_ELEVATION,
        tonalElevation = TOGGLE_TONAL_ELEVATION,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(mainIcon, contentDescription = mainContentDescription)
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

// Dimension defaults
private object DefaultDimensions {
    val ItemHeight = 48.dp
    val ItemSpacing = 12.dp
    val RailWidth = 160.dp
    val CornerRadius = 24.dp
}

// Animation constants
private const val ITEM_STAGGER_BASE_MS = 40
private const val FADE_IN_DURATION_MS = 90
private const val EXPAND_DURATION_MS = 160
private const val FADE_OUT_DURATION_MS = 80
private const val SHRINK_DURATION_MS = 120
private const val SLIDE_DIVISOR = 5
private const val SCRIM_ALPHA = 0.32f
private val TOGGLE_BUTTON_SIZE = 56.dp
private val TOGGLE_SHADOW_ELEVATION = 6.dp
private val TOGGLE_TONAL_ELEVATION = 3.dp
