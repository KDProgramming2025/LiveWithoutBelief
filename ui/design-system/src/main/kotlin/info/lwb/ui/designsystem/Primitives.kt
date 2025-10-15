/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Simple square preview block rendered using the current theme's primary color.
 *
 * Useful in design previews or debug tooling to visualize the active primary color token.
 *
 * @param modifier Modifier chain for sizing/styling; by default a 64.dp square.
 */
@Composable
fun ColorSwatch(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/**
 * Simple raised button abstraction used across the app. Wraps Material3 [Button] but centralizes
 * styling tokens so call sites only provide text & click handler.
 *
 * @param text Label displayed inside the button.
 * @param onClick Click callback.
 * @param modifier Optional modifier for layout/styling.
 * @param enabled Whether the button is enabled.
 * @param containerColor Override for container, defaults to primary.
 */
@Composable
fun RaisedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    ) { Text(text) }
}
