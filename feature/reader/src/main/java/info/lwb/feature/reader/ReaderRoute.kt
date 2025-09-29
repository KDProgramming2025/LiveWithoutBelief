/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.model.Article
import info.lwb.core.model.ArticleContent
import info.lwb.feature.reader.ui.ActionRail
import info.lwb.feature.reader.ui.ActionRailItem
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.ArticleWebView
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
import info.lwb.feature.reader.ui.loadAssetText
import info.lwb.feature.reader.ui.readerPalette
import info.lwb.feature.reader.ui.themeCssAssetPath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Inline html-based ReaderRoute has been removed; use ReaderByIdRoute(articleId) exclusively.

/**
 * Route that loads the article content by id from the repository and forwards to ReaderScreen.
 */
@Composable
internal fun ReaderByIdRoute(
    articleId: String,
    onNavigateBack: (() -> Unit)? = null,
    vm: ReaderViewModel = hiltViewModel(),
) {
    // For MVP, fetch content using GetArticleContentUseCase via the feature.viewmodels.ReaderViewModel
    val svcVm: info.lwb.feature.reader.viewmodels.ReaderViewModel = hiltViewModel()
    LaunchedEffect(articleId) { svcVm.loadArticleContent(articleId) }
    val contentRes by svcVm.articleContent.collectAsState()
    val ui by vm.uiState.collectAsState()
    val articlesRes by svcVm.articles.collectAsState()
    val env: ReaderEnv = hiltViewModel()
    // Resolve a URL to load: prefer server-provided indexUrl, else compute from slug using Admin web base derived from API base.
    val resolvedUrl: String? = remember(contentRes, articlesRes, env.apiBaseUrl, articleId) {
        val viaContent = (contentRes as? Result.Success<ArticleContent>)?.data?.indexUrl
        if (!viaContent.isNullOrBlank()) return@remember viaContent
        val list = (articlesRes as? Result.Success<List<Article>>)?.data
        val slug = list?.firstOrNull { it.id == articleId }?.slug
        if (!slug.isNullOrBlank()) {
            val api = env.apiBaseUrl.trimEnd('/')
            val adminBase = api.replace(Regex("/API/?$"), "/Admin/").trimEnd('/')
            "$adminBase/web/articles/$slug/"
        } else {
            null
        }
    }

    if (!resolvedUrl.isNullOrBlank()) {
        // When rendering server URL in a WebView, we still want the FAB behavior.
        var fabVisible by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var confirmExit by remember { mutableStateOf(false) }
        fun showFabTemporarily() {
            fabVisible = true
            hideJob?.cancel()
            hideJob = scope.launch {
                kotlinx.coroutines.delay(5000)
                fabVisible = false
            }
        }
        LaunchedEffect(resolvedUrl) { showFabTemporarily() }
        DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }
        var showAppearance by remember { mutableStateOf(false) }
        BackHandler(enabled = true) {
            when {
                showAppearance -> {
                    showAppearance = false
                }
                fabVisible -> {
                    fabVisible = false
                }
                else -> {
                    confirmExit = true
                }
            }
        }
        val scrollVm: ScrollViewModel = hiltViewModel()
        var initialScroll by remember { mutableStateOf(0) }
        var initialAnchor by remember { mutableStateOf("") }
        // Load initial scroll value once
        LaunchedEffect(articleId) {
            initialScroll = try {
                scrollVm.observe(articleId).first()
            } catch (_: Throwable) {
                0
            }
            initialAnchor = try {
                scrollVm.observeAnchor(articleId).first()
            } catch (_: Throwable) {
                ""
            }
        }
        Box(Modifier.fillMaxSize()) {
            val css = run {
                val ctx = LocalContext.current
                val path = themeCssAssetPath(readerPalette(ui.background))
                try {
                    loadAssetText(ctx, path)
                } catch (_: Throwable) {
                    ""
                }
            }
            Scaffold { padding ->
                ArticleWebView(
                    url = resolvedUrl,
                    injectedCss = css,
                    fontScale = ui.fontScale.toFloat(),
                    lineHeight = ui.lineHeight.toFloat(),
                    backgroundColor = readerPalette(ui.background).background,
                    modifier = Modifier.padding(padding),
                    onTap = { showFabTemporarily() },
                    initialScrollY = initialScroll,
                    initialAnchor = initialAnchor.takeIf { it.isNotBlank() },
                    onScrollChanged = { y ->
                        scope.launch { scrollVm.save(articleId, y) }
                    },
                    onAnchorChanged = { a -> scope.launch { scrollVm.saveAnchor(articleId, a) } },
                )
            }
            if (fabVisible) {
                ActionRail(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    items = listOf(
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.Settings,
                            label = "Appearance",
                            onClick = { showAppearance = true },
                        ),
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.Edit,
                            label = "Bookmark",
                            onClick = { showFabTemporarily() },
                        ),
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                            label = "Listen",
                            onClick = { showFabTemporarily() },
                        ),
                    ),
                    mainIcon = androidx.compose.material.icons.Icons.Filled.Settings,
                    mainContentDescription = "Reader actions",
                    edgePadding = 16.dp,
                )
            }
            if (showAppearance) {
                val vmAppearance = AppearanceState(
                    fontScale = ui.fontScale,
                    lineHeight = ui.lineHeight,
                    background = ui.background,
                    onFontScale = vm::onFontScaleChange,
                    onLineHeight = vm::onLineHeightChange,
                    onBackground = vm::onBackgroundChange,
                )
                ReaderAppearanceSheet(
                    visible = showAppearance,
                    state = vmAppearance,
                    onDismiss = { showAppearance = false },
                )
            }
            if (confirmExit) {
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text("Leave reader?") },
                    text = { Text("Are you sure you want to exit the reader?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmExit = false
                            // Prefer explicit navigation callback to avoid BackHandler loop
                            onNavigateBack?.invoke() ?: backDispatcher?.onBackPressed()
                        }) { Text("Exit") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmExit = false }) { Text("Cancel") }
                    },
                )
            }
        }
    } else {
        // If URL cannot be resolved, show a minimal load/error placeholder without attempting inline fallback.
        when (contentRes) {
            is Result.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else -> Box(modifier = Modifier.fillMaxSize()) {
                Text("Content not found", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// Minimal Hilt VM to surface environment strings
@dagger.hilt.android.lifecycle.HiltViewModel
internal class ReaderEnv @javax.inject.Inject constructor(
    @javax.inject.Named("apiBaseUrl") val apiBaseUrl: String,
) : androidx.lifecycle.ViewModel()
