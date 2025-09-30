/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.model.Article
import info.lwb.feature.settings.SettingsViewModel
import info.lwb.feature.settings.ThemeMode
import info.lwb.ui.designsystem.GrainyBackground
import info.lwb.ui.designsystem.LocalSurfaceStyle
import info.lwb.ui.designsystem.ProvideSurfaceStyle
import info.lwb.ui.designsystem.RaisedIconWell
import info.lwb.ui.designsystem.RaisedSurface

/**
 * High level route composable that wires up the [ArticleListByLabelViewModel] and renders the
 * appropriate UI state (loading, error, empty, list). Theme selection follows user settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListByLabelRoute(
    label: String,
    onArticleClick: (Article) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val vm: ArticleListByLabelViewModel = hiltViewModel()
    LaunchedEffect(label) { vm.load(label) }
    val state by vm.state.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val themeMode by settingsVm.themeMode.collectAsState()
    val dark = when (themeMode) {
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
    ProvideSurfaceStyle(dark = dark) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Breadcrumb(
                            segments = listOf(
                                Crumb("Home", onNavigateHome),
                                Crumb("Articles", null),
                            ),
                        )
                    },
                )
            },
        ) { padding ->
            ArticleListByLabelContent(
                state = state,
                paddingValues = padding,
                onRetry = { vm.load(state.label) },
                onNavigateBack = onNavigateBack,
                onArticleClick = onArticleClick,
            )
        }
    }
}

@Composable
private fun ArticleListByLabelContent(
    state: ArticleListByLabelViewModel.UiState,
    paddingValues: PaddingValues,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onArticleClick: (Article) -> Unit,
) {
    val neo = LocalSurfaceStyle.current
    Box(Modifier.fillMaxSize()) {
        GrainyBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.loading -> {
                    LoadingState(neo)
                }
                state.error != null -> {
                    ErrorState(message = state.error ?: "Error", neo = neo, onRetry = onRetry)
                }
                state.items.isEmpty() -> {
                    EmptyState(neo = neo, onNavigateBack = onNavigateBack)
                }
                else -> {
                    ArticlesList(items = state.items, onArticleClick = onArticleClick)
                }
            }
        }
    }
}

@Composable
private fun LoadingState(neo: info.lwb.ui.designsystem.SurfaceStyle) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            "Loading articles…",
            style = MaterialTheme.typography.bodyMedium,
            color = neo.textPrimary,
        )
    }
}

@Composable
private fun ErrorState(message: String, neo: info.lwb.ui.designsystem.SurfaceStyle, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = neo.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState(neo: info.lwb.ui.designsystem.SurfaceStyle, onNavigateBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "No articles found.",
            style = MaterialTheme.typography.bodyMedium,
            color = neo.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNavigateBack) { Text("Go back") }
    }
}

@Composable
private fun ArticlesList(items: List<Article>, onArticleClick: (Article) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        items(items) { a ->
            ArticleCard(
                title = a.title,
                coverUrl = a.coverUrl,
                iconUrl = a.iconUrl,
                onClick = { onArticleClick(a) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ——— UI bits
// Internal only: keep private to avoid unnecessary public API & documentation requirements.
private data class Crumb(val label: String, val onClick: (() -> Unit)?)

@Composable
private fun Breadcrumb(segments: List<Crumb>) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, crumb ->
            val isLast = index == segments.lastIndex
            FilterChip(
                selected = isLast,
                onClick = {
                    if (!isLast) {
                        crumb.onClick?.invoke()
                    }
                },
                enabled = !isLast && crumb.onClick != null,
                label = { Text(crumb.label) },
            )
            if (index != segments.lastIndex) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun ArticleCard(title: String, coverUrl: String?, iconUrl: String?, onClick: () -> Unit) {
    RaisedSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            ArticleCover(coverUrl = coverUrl, iconUrl = iconUrl)
            ArticleTitleRow(title = title, iconUrl = iconUrl)
        }
    }
}

@Composable
private fun ArticleCover(coverUrl: String?, iconUrl: String?) {
    when {
        !coverUrl.isNullOrBlank() -> {
            coil.compose.AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
        }
        !iconUrl.isNullOrBlank() -> {
            coil.compose.AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Fit,
            )
        }
        else -> {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )
        }
    }
}

@Composable
private fun ArticleTitleRow(title: String, iconUrl: String?) {
    val neo = LocalSurfaceStyle.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RaisedIconWell(wellSize = 48.dp, innerPadding = 6.dp) {
            if (!iconUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title.take(1).uppercase(),
                        color = neo.textMuted,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = neo.textPrimary,
        )
    }
}
