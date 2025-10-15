/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun SurfaceStyleShowcase(dark: Boolean) {
    ProvideSurfaceStyle(dark = dark) {
        val colors = LocalSurfaceStyle.current
        Surface(Modifier.fillMaxSize()) {
            GrainyBackground(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (dark) {
                        "SurfaceStyle – Dark"
                    } else {
                        "SurfaceStyle – Light"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RaisedButton(onClick = {}) { Text("Primary", color = colors.textPrimary) }
                    RaisedButton(onClick = {}) { Text("Action", color = colors.textPrimary) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RaisedSurface { Text("Card A", modifier = Modifier.padding(14.dp), color = colors.textPrimary) }
                    RaisedSurface { Text("Card B", modifier = Modifier.padding(14.dp), color = colors.textPrimary) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InsetIconWell(wellSize = 56.dp) {}
                    InsetIconWell(wellSize = 72.dp) {}
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Preview(name = "SurfaceStyle – Light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PreviewSurfaceStyleLight() {
    SurfaceStyleShowcase(dark = false)
}

@Preview(name = "SurfaceStyle – Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PreviewSurfaceStyleDark() {
    SurfaceStyleShowcase(dark = true)
}
