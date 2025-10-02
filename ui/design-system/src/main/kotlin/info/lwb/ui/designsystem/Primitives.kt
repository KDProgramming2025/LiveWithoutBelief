/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */

package info.lwb.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
