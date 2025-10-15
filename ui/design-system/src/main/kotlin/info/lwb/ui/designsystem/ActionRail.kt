/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("SpacingBetweenDeclarationsWithComments")

package info.lwb.ui.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A floating toggle that opens a bottom sheet for contextual quick actions.
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
@Suppress("UnusedParameter")
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
        // Bottom-sheet presentation for global actions
        if (expanded) {
            ActionBottomSheet(
                items = items,
                onDismiss = { expanded = false },
                header = items.firstOrNull { it.role == ActionRole.Header },
                footer = items.firstOrNull { it.role == ActionRole.Footer },
                onItemClick = { item ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    expanded = false
                    item.onClick()
                },
            )
        }
        // Floating toggle retained; placed at bottom-end with padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = edgePadding, bottom = edgePadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            RailToggleButton(
                onToggle = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    expanded = true
                },
                mainIcon = mainIcon,
                mainContentDescription = mainContentDescription,
            )
        }
    }
}

/**
 * Distinguishes how an action appears in the global actions bottom sheet.
 *
 * Normal actions are listed in the body; a single Header item (if provided) is rendered at the
 * top as an identity summary; a single Footer item (if provided) is rendered at the bottom, used
 * commonly for a destructive action like Sign out.
 */
enum class ActionRole {
    /** Default role for standard sheet actions. */
    Normal,
    /** Optional header row at the top, usually showing the signed-in user. */
    Header,
    /** Optional footer row at the bottom, often a sign-out/destructive action. */
    Footer,
}

/**
 * Describes a single action displayed in the [ActionRail]'s bottom sheet.
 * @property icon Vector icon shown alongside the label.
 * @property label One-line, user-facing label.
 * @property role Placement role within the bottom sheet (header/body/footer).
 * @property onClick Executed after feedback when tapped.
 */
data class ActionRailItem(
    val icon: ImageVector,
    val label: String,
    val role: ActionRole = ActionRole.Normal,
    val onClick: () -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ActionBottomSheet(
    items: List<ActionRailItem>,
    header: ActionRailItem?,
    footer: ActionRailItem?,
    onItemClick: (ActionRailItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            SheetHeader(header = header, onClick = onItemClick)
            SheetBody(items = items, onItemClick = onItemClick)
            SheetFooter(footer = footer, onClick = onItemClick)
        }
    }
}

@Composable
private fun SheetHeader(header: ActionRailItem?, onClick: (ActionRailItem) -> Unit) {
    if (header == null) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button) { onClick(header) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            initials = header.label
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                header.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Signed in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetBody(items: List<ActionRailItem>, onItemClick: (ActionRailItem) -> Unit) {
    val bodyItems = items.filter { it.role == ActionRole.Normal }
    bodyItems.forEachIndexed { index, item ->
        ListItem(
            leadingContent = {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { onItemClick(item) },
        )
        if (index < bodyItems.lastIndex) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun SheetFooter(footer: ActionRailItem?, onClick: (ActionRailItem) -> Unit) {
    if (footer == null) {
        return
    }
    Spacer(Modifier.height(8.dp))
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))
    ListItem(
        leadingContent = {
            Icon(
                footer.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        headlineContent = {
            Text(
                text = footer.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button) { onClick(footer) },
    )
}

@Composable
private fun Avatar(initials: String) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// Deprecated pill UI kept for potential reuse in other places.

@Composable
private fun RailToggleButton(onToggle: () -> Unit, mainIcon: ImageVector, mainContentDescription: String) {
    Surface(
        modifier = Modifier
            .size(TOGGLE_BUTTON_SIZE)
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
private val TOGGLE_BUTTON_SIZE = 56.dp
private val TOGGLE_SHADOW_ELEVATION = 6.dp
private val TOGGLE_TONAL_ELEVATION = 3.dp
