/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import info.lwb.core.model.MenuItem
import info.lwb.feature.settings.SettingsViewModel
import info.lwb.feature.settings.ThemeMode
import info.lwb.ui.designsystem.ActionRail
import info.lwb.ui.designsystem.ActionRailItem
import info.lwb.ui.designsystem.GrainyBackground
import info.lwb.ui.designsystem.LocalSurfaceStyle
import info.lwb.ui.designsystem.ProvideSurfaceStyle
import info.lwb.ui.designsystem.RaisedButton
import info.lwb.ui.designsystem.RaisedIconWell
import info.lwb.ui.designsystem.RaisedSurface

/**
 * Entry point composable for the Home feature.
 *
 * Responsibilities:
 * 1. Obtains [HomeViewModel] and [SettingsViewModel].
 * 2. Determines the active dark theme flag based on [ThemeMode].
 * 3. Delegates UI rendering to [HomeScreen] based on a [HomeUiState] value.
 *
 * Detekt considerations:
 * - All public composables documented (comments.UndocumentedPublicFunction).
 * - No magic numbers: dimension & spacing constants centralized.
 * - Complexity kept low by delegating branches to separate composables.
 */
@Composable
fun HomeRoute(
    onItemClick: (String, String?) -> Unit = { _, _ -> },
    onContinueReading: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val uiState by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    // Idempotent initial load (will no-op on subsequent recompositions / returns to Home)
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val darkTheme =
        when (themeMode) {
            ThemeMode.LIGHT -> {
                false
            }
            ThemeMode.DARK -> {
                true
            }
            ThemeMode.SYSTEM -> {
                isSystemInDarkTheme()
            }
        }

    ProvideSurfaceStyle(dark = darkTheme) {
        HomeScreen(
            state = uiState,
            uploadsBaseUrl = viewModel.uploadsBaseUrl,
            onItemClick = onItemClick,
            onContinueReading = onContinueReading,
            onOpenSettings = onOpenSettings,
            refreshing = refreshing,
            onRefresh = { viewModel.refresh() },
        )
    }
}

/** Spacing & size constants to avoid MagicNumber violations. */
private object HomeDimens {
    val HorizontalPadding = 16.dp
    val TopSpacerLarge = 12.dp
    val TopSpacerSmall = 8.dp
    val GridSpacing = 8.dp
    val CardPadding = 14.dp
    val IconWellSize = 70.dp
    val IconWellInnerPadding = 6.dp
    val IconTitleSpacing = 10.dp
    val GridContentTop = 4.dp
    val GridContentBottom = 16.dp
    val CardMinHeight = 112.dp
    val GridAdaptiveMin = 240.dp
}

/**
 * High level screen that renders background & delegates to specific content state composables.
 */
@Composable
@OptIn(ExperimentalMaterialApi::class)
internal fun HomeScreen(
    state: HomeUiState,
    uploadsBaseUrl: String,
    onItemClick: (String, String?) -> Unit,
    onContinueReading: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val surface = LocalSurfaceStyle.current
    val pullState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pullRefresh(pullState)) {
            GrainyBackground(Modifier.fillMaxSize())
            when (state) {
                is HomeUiState.Loading -> {
                    HomeLoading()
                }
                is HomeUiState.Error -> {
                    HomeError(
                        message = state.message,
                        onRetry = onRefresh,
                    )
                }
                is HomeUiState.Success -> {
                    HomeSuccess(
                        items = state.items,
                        uploadsBaseUrl = uploadsBaseUrl,
                        onItemClick = onItemClick,
                        onContinueReading = onContinueReading,
                        onOpenSettings = onOpenSettings,
                        surfaceTextPrimary = surface.textPrimary,
                        surfaceTextMuted = surface.textMuted,
                    )
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    val surface = LocalSurfaceStyle.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = surface.textPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        RaisedButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun HomeSuccess(
    items: List<MenuItem>,
    uploadsBaseUrl: String,
    onItemClick: (String, String?) -> Unit,
    onContinueReading: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    surfaceTextPrimary: androidx.compose.ui.graphics.Color,
    surfaceTextMuted: androidx.compose.ui.graphics.Color,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(
                modifier = Modifier.height(HomeDimens.TopSpacerLarge),
            )
            ContinueReadingBar(
                onContinueReading = onContinueReading,
                textColor = surfaceTextPrimary,
            )
            Spacer(
                modifier = Modifier.height(HomeDimens.TopSpacerSmall),
            )
            MenuGrid(
                items = items,
                uploadsBaseUrl = uploadsBaseUrl,
                onItemClick = onItemClick,
                textPrimary = surfaceTextPrimary,
                textMuted = surfaceTextMuted,
            )
        }
        HomeActionsOverlay(onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun BoxScope.HomeActionsOverlay(onOpenSettings: () -> Unit) {
    // Simple always-visible action rail; could be stateful later
    ActionRail(
        modifier = Modifier.align(Alignment.BottomEnd),
        items = listOf(
            ActionRailItem(Icons.Filled.Settings, "Settings") { onOpenSettings() },
            ActionRailItem(Icons.Filled.Star, "Bookmarks") { /* TODO hook bookmarks */ },
            ActionRailItem(Icons.Filled.PlayArrow, "Comments") { /* TODO hook comments */ },
        ),
        mainIcon = Icons.Filled.Settings,
        mainContentDescription = "Home quick actions",
        edgePadding = 16.dp,
    )
}

@Composable
private fun ContinueReadingBar(onContinueReading: (() -> Unit)?, textColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeDimens.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onContinueReading != null) {
            RaisedButton(onClick = onContinueReading) {
                Text(
                    text = "Continue reading",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// Menu grid presenting menu items in adaptive columns.
@Composable
private fun MenuGrid(
    items: List<MenuItem>,
    uploadsBaseUrl: String,
    onItemClick: (String, String?) -> Unit,
    textPrimary: androidx.compose.ui.graphics.Color,
    textMuted: androidx.compose.ui.graphics.Color,
) {
    val sorted = remember(items) {
        items.sortedWith(
            compareBy(
                { it.order },
                { it.title },
            ),
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = HomeDimens.GridAdaptiveMin),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(HomeDimens.GridSpacing),
        verticalArrangement = Arrangement.spacedBy(HomeDimens.GridSpacing),
        contentPadding = PaddingValues(
            start = HomeDimens.HorizontalPadding - 4.dp,
            end = HomeDimens.HorizontalPadding - 4.dp,
            top = HomeDimens.GridContentTop,
            bottom = HomeDimens.GridContentBottom,
        ),
    ) {
        items(sorted, key = { it.id }) { item ->
            val normalized = normalizeIconPath(item.iconPath)
            val imageUrl = if (normalized != null) {
                uploadsBaseUrl.trimEnd('/') + "/" + normalized
            } else {
                null
            }
            NeoMenuCard(
                title = item.title,
                imageUrl = imageUrl,
                onClick = {
                    val fallbackLabel = item.label?.takeIf { l -> l.isNotBlank() } ?: item.id
                    onItemClick(item.id, fallbackLabel)
                },
                textPrimary = textPrimary,
                textMuted = textMuted,
            )
        }
    }
}

private fun normalizeIconPath(raw: String?): String? {
    if (raw.isNullOrBlank()) {
        return null
    }
    return if (raw.startsWith("/uploads")) {
        raw.removePrefix("/uploads").trimStart('/')
    } else {
        raw.trimStart('/')
    }
}

@Composable
private fun NeoMenuCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    minHeight: Dp = HomeDimens.CardMinHeight,
    textPrimary: androidx.compose.ui.graphics.Color,
    textMuted: androidx.compose.ui.graphics.Color,
) {
    RaisedSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = minHeight),
    ) {
        Box(
            modifier = Modifier.padding(HomeDimens.CardPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RaisedIconWell(
                    wellSize = HomeDimens.IconWellSize,
                    innerPadding = HomeDimens.IconWellInnerPadding,
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = title.take(1).uppercase(),
                                color = textMuted,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(HomeDimens.IconTitleSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                        color = textPrimary,
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
        val items = remember {
            listOf(
                PreviewItem("1", "Cosmology vs. Scripture", null),
                PreviewItem("2", "Geology and Flood Myths", null),
                PreviewItem("3", "Evolution and Design Claims", null),
                PreviewItem("4", "Moral Philosophy without Dogma", null),
                PreviewItem("5", "Historical Criticism of Texts", null),
            )
        }
        HomeScreen(
            state = HomeUiState.Success(
                items.map {
                    MenuItem(
                        id = it.id,
                        title = it.title,
                        label = null,
                        order = 0,
                        iconPath = null,
                    )
                },
            ),
            uploadsBaseUrl = "https://example.test/uploads",
            onItemClick = { _, _ -> },
            onContinueReading = {},
            onOpenSettings = {},
            refreshing = false,
            onRefresh = {},
        )
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
