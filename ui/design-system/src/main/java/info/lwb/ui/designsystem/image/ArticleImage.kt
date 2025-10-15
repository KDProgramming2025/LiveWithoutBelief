/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.ui.designsystem.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageScope

/**
 * Unified image component for article visuals.
 * Shows a progress indicator while loading and a minimal textual fallback on error.
 */
@Composable
fun ArticleImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    clip: Boolean = false,
) {
    val appliedModifier = if (clip) {
        modifier.clip(MaterialTheme.shapes.medium)
    } else {
        modifier
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = appliedModifier,
        contentScale = contentScale,
        loading = { LoadingState() },
        error = { ErrorState(contentDescription) },
    )
}

@Composable
private fun SubcomposeAsyncImageScope.LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun SubcomposeAsyncImageScope.ErrorState(description: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (description?.takeIf { it.isNotBlank() }?.take(1) ?: "?"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
