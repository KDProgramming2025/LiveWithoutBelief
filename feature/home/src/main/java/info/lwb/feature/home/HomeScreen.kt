/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import info.lwb.feature.settings.SettingsViewModel
import info.lwb.feature.settings.ThemeMode
import info.lwb.ui.designsystem.GrainyBackground
import info.lwb.ui.designsystem.LocalSurfaceStyle
import info.lwb.ui.designsystem.ProvideSurfaceStyle
import info.lwb.ui.designsystem.RaisedButton
import info.lwb.ui.designsystem.RaisedIconWell
import info.lwb.ui.designsystem.RaisedSurface

/**
 * Root composable for the home feature. Decides what to render based on [HomeUiState].
 *
 * @param onItemClick invoked when a menu item is selected with its id and optional label.
 * @param onContinueReading optional callback to resume last read article.
 */
@Composable
fun HomeRoute(onItemClick: (String, String?) -> Unit = { _, _ -> }, onContinueReading: (() -> Unit)? = null) {
    val vm: HomeViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val themeMode by settingsVm.themeMode.collectAsState()
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    ProvideSurfaceStyle(dark = dark) {
        val neo = LocalSurfaceStyle.current
        Surface(Modifier.fillMaxSize()) {
            GrainyBackground(Modifier.fillMaxSize())
            when (val s = state) {
                is HomeUiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                is HomeUiState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Failed to load menu",
                        color = neo.textPrimary,
                    )
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = neo.textMuted,
                    )
                }
                is HomeUiState.Success -> {
                    val horizontalPadding = 16.dp
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            if (onContinueReading != null) {
                                RaisedButton(onClick = onContinueReading) {
                                    Text(
                                        "Continue reading",
                                        color = neo.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val sorted = s.items.sortedWith(
                            compareBy(
                                { it.order },
                                { it.title },
                            ),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 240.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = horizontalPadding - 4.dp,
                                end = horizontalPadding - 4.dp,
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                        ) {
                            items(sorted, key = { it.id }) { item ->
                                val iconPathVal = item.iconPath
                                val normalized = if (!iconPathVal.isNullOrBlank() && iconPathVal.startsWith(
                                        "/uploads",
                                    )
                                ) {
                                    iconPathVal.removePrefix("/uploads")
                                } else {
                                    iconPathVal ?: ""
                                }
                                val imageUrl = if (normalized.isNotBlank()) {
                                    vm.uploadsBaseUrl.trimEnd('/') + "/" +
                                        normalized.trimStart('/')
                                } else {
                                    null
                                }

                                NeoMenuCard(
                                    title = item.title,
                                    imageUrl = imageUrl,
                                    onClick = { onItemClick(item.id, item.label) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeoMenuCard(title: String, imageUrl: String?, onClick: () -> Unit, minHeight: Dp = 112.dp) {
    val neo = LocalSurfaceStyle.current
    RaisedSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = minHeight),
    ) {
        Box(
            modifier = Modifier
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Make icon well 25% larger (56dp -> 70.dp) and add small inner padding
                RaisedIconWell(wellSize = 70.dp, innerPadding = 6.dp) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                title.take(1).uppercase(),
                                color = neo.textMuted,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                        color = neo.textPrimary,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

// ----- Previews -----

private data class PreviewItem(val id: String, val title: String, val iconUrl: String?)

@Composable
private fun HomeMenuPreviewContent(dark: Boolean) {
    ProvideSurfaceStyle(dark = dark) {
        val neo = LocalSurfaceStyle.current
        val items = remember {
            listOf(
                PreviewItem("1", "Cosmology vs. Scripture", null),
                PreviewItem("2", "Geology and Flood Myths", null),
                PreviewItem("3", "Evolution and Design Claims", null),
                PreviewItem("4", "Moral Philosophy without Dogma", null),
                PreviewItem("5", "Historical Criticism of Texts", null),
            )
        }
        Surface(Modifier.fillMaxSize()) {
            GrainyBackground(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    RaisedButton(onClick = {}) {
                        Text(
                            "Continue reading",
                            color = neo.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 240.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                        NeoMenuCard(
                            title = item.title,
                            imageUrl = item.iconUrl,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Home Menu - Light",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun PreviewHomeMenuLight() {
    HomeMenuPreviewContent(dark = false)
}

@Preview(
    name = "Home Menu - Dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun PreviewHomeMenuDark() {
    HomeMenuPreviewContent(dark = true)
}
