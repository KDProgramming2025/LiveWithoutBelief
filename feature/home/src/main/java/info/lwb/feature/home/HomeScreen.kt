/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            is HomeUiState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                items(s.items, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!item.iconPath.isNullOrBlank()) {
                            AsyncImage(
                                model = vm.apiBaseUrl.trimEnd('/') + item.iconPath,
                                contentDescription = null,
                                modifier = Modifier.height(40.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val label = item.label
                            if (!label.isNullOrBlank()) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
