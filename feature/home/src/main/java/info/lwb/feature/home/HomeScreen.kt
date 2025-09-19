/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun HomeRoute(onItemClick: (String) -> Unit = {}) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            is HomeUiState.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            is HomeUiState.Error -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Failed to load menu")
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is HomeUiState.Success -> {
                val horizontalPadding = 16.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = horizontalPadding, end = horizontalPadding, top = 16.dp, bottom = 24.dp
                    ),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        val iconPathVal = item.iconPath
                        val normalized = if (!iconPathVal.isNullOrBlank() && iconPathVal.startsWith("/uploads")) {
                            iconPathVal.removePrefix("/uploads")
                        } else iconPathVal ?: ""
                        val imageUrl = if (normalized.isNotBlank()) vm.uploadsBaseUrl.trimEnd('/') + "/" + normalized.trimStart('/') else null

                        MenuCard(
                            title = item.title,
                            imageUrl = imageUrl,
                            onClick = { onItemClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    height: Dp = 148.dp,
) {
    val container = MaterialTheme.colorScheme.surface
    val onContainer = MaterialTheme.colorScheme.onSurface
    val accent1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val accent2 = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val gradient = Brush.linearGradient(listOf(accent1, Color.Transparent, accent2))

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(14.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Icon block
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // Elegant fallback when icon is missing: colored square with initial
                    val bg = MaterialTheme.colorScheme.primaryContainer
                    val fg = MaterialTheme.colorScheme.onPrimaryContainer
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .height(48.dp)
                            .width(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        drawRect(bg)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(title.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
