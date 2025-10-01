/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider

// Slider range constants
private const val FONT_SCALE_MIN = 0.8
private const val FONT_SCALE_MAX = 1.6
private const val LINE_HEIGHT_MIN = 1.0
private const val LINE_HEIGHT_MAX = 2.0
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// imports grouped above
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.lwb.feature.reader.ui.ActionRail
import info.lwb.feature.reader.ui.ActionRailItem
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
// Constants for reader control slider ranges.

@Composable
internal fun BoxScope.ReaderScreenOverlays(
    appearance: AppearanceState?,
    showAppearance: Boolean,
    onDismissAppearance: () -> Unit,
    confirmExit: Boolean,
    onDismissExit: () -> Unit,
    onExitConfirmed: () -> Unit,
    fabVisible: Boolean,
    onFabBookmark: () -> Unit,
    onFabListen: () -> Unit,
    onFabAppearance: () -> Unit,
) {
    if (fabVisible) {
        ActionRail(
            modifier = Modifier.align(Alignment.BottomEnd),
            items = listOf(
                ActionRailItem(
                    icon = Icons.Filled.Settings,
                    label = "Appearance",
                    onClick = { onFabAppearance() },
                ),
                ActionRailItem(
                    icon = Icons.Filled.Edit,
                    label = "Bookmark",
                    onClick = { onFabBookmark() },
                ),
                ActionRailItem(
                    icon = Icons.Filled.PlayArrow,
                    label = "Listen",
                    onClick = { onFabListen() },
                ),
            ),
            mainIcon = Icons.Filled.Settings,
            mainContentDescription = "Reader actions",
            edgePadding = 16.dp,
        )
    }
    if (appearance != null) {
        ReaderAppearanceSheet(
            visible = showAppearance,
            state = appearance,
            onDismiss = onDismissAppearance,
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = onDismissExit,
            title = { Text("Leave reader?") },
            text = { Text("Are you sure you want to exit the reader?") },
            confirmButton = { TextButton(onClick = onExitConfirmed) { Text("Exit") } },
            dismissButton = { TextButton(onClick = onDismissExit) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ReaderControlsBar(settings: ReaderSettingsState, onChange: (Double, Double) -> Unit) {
    Surface(
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                "Font: ${"%.2f".format(settings.fontScale)}  Line: ${"%.2f".format(settings.lineHeight)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontScale.toFloat(),
                    onValueChange = {
                        onChange(
                            it.toDouble().coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX),
                            settings.lineHeight,
                        )
                    },
                    valueRange = FONT_SCALE_MIN.toFloat()..FONT_SCALE_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = settings.lineHeight.toFloat(),
                    onValueChange = {
                        onChange(
                            settings.fontScale,
                            it.toDouble().coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX),
                        )
                    },
                    valueRange = LINE_HEIGHT_MIN.toFloat()..LINE_HEIGHT_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun SearchBar(
    query: String,
    occurrences: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search") },
        )
        if (occurrences > 0) {
            Text(
                "$currentIndex/$occurrences",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
            Button(onClick = onPrev, enabled = occurrences > 0) { Text("Prev") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onNext, enabled = occurrences > 0) { Text("Next") }
        }
    }
}
