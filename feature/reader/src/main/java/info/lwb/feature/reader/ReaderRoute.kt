/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.model.ArticleContent
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.ArticleWebView
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
import info.lwb.feature.reader.ui.loadAssetText
import info.lwb.feature.reader.ui.readerPalette
import info.lwb.feature.reader.ui.themeCssAssetPath
import info.lwb.ui.designsystem.ActionRail
import info.lwb.ui.designsystem.ActionRailItem
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

private const val FAB_HIDE_DELAY_MS = 5_000L
private val ACTION_RAIL_EDGE_PADDING = 16.dp

// Inline html-based ReaderRoute has been removed; use ReaderByIdRoute(articleId) exclusively.

/**
 * Route that loads the article content by id, resolves the server WebView URL and renders the
 * reader experience (web content + action rail + appearance sheet + exit dialog).
 *
 * High level orchestration only; heavy UI logic is split into smaller composables to keep
 * complexity low and satisfy Detekt rules.
 */
@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ReaderByIdRoute(articleId: String, onNavigateBack: (() -> Unit)? = null) {
    val contentVm: ReaderContentViewModel = hiltViewModel()
    LaunchedEffect(articleId) { contentVm.load(articleId) }
    val refreshing by contentVm.refreshing.collectAsState()
    val contentRes by contentVm.content.collectAsState()
    val env: ReaderEnv = hiltViewModel()
    val vm: ReaderSessionViewModel = hiltViewModel()
    val ui by vm.uiState.collectAsState()

    val resolvedUrl = remember(contentRes, env.apiBaseUrl, articleId) {
        resolveArticleUrl(articleId, contentRes, env.apiBaseUrl)
    }

    val pullState = rememberPullRefreshState(refreshing = refreshing, onRefresh = { contentVm.refresh() })
    Box(Modifier.fillMaxSize().pullRefresh(pullState)) {
        if (!resolvedUrl.isNullOrBlank()) {
            ReaderResolvedContent(
                articleId = articleId,
                resolvedUrl = resolvedUrl,
                uiState = ui,
                onNavigateBack = onNavigateBack,
                readerViewModel = vm,
            )
        } else {
            ReaderLoadingOrError(contentRes)
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// Minimal Hilt VM to surface environment strings
@HiltViewModel
internal class ReaderEnv @Inject constructor(@Named("apiBaseUrl") val apiBaseUrl: String) : ViewModel()

//region Helpers & extracted composables

private fun resolveArticleUrl(
    articleId: String,
    contentRes: Result<ArticleContent>?,
    apiBaseUrl: String,
): String? {
    val viaContent = (contentRes as? Result.Success<ArticleContent>)?.data?.indexUrl
    if (!viaContent.isNullOrBlank()) {
        return viaContent
    }
    // Fallback: construct path using id if indexUrl absent (slug no longer available here).
    val api = apiBaseUrl.trimEnd('/')
    val adminBase = api.replace(Regex("/API/?$"), "/Admin/").trimEnd('/')
    return "$adminBase/web/articles/$articleId/"
}

// (Removed older ReaderResolvedContent implementation replaced by shorter version below)

@Composable
private fun ReaderLoadingOrError(contentRes: Result<ArticleContent>?) {
    if (contentRes is Result.Loading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Text("Content not found", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ConfirmExitDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Leave reader?") },
        text = { Text("Are you sure you want to exit the reader?") },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) { Text("Exit") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        },
    )
}

@Composable
private fun loadCssOrBlank(uiStateBackground: ReaderSettingsRepository.ReaderBackground): String {
    val ctx = LocalContext.current
    val path = themeCssAssetPath(readerPalette(uiStateBackground))
    return try {
        loadAssetText(ctx, path)
    } catch (_: Throwable) {
        ""
    }
}

//endregion

//region Extracted smaller composables to reduce ReaderResolvedContent length

@Composable
private fun ReaderWebViewScaffold(
    articleId: String,
    resolvedUrl: String,
    css: String,
    uiState: ReaderUiState,
    onTap: () -> Unit,
) {
    Scaffold { padding ->
        ArticleWebView(
            articleId = articleId,
            url = resolvedUrl,
            injectedCss = css,
            fontScale = uiState.fontScale.toFloat(),
            lineHeight = uiState.lineHeight.toFloat(),
            backgroundColor = readerPalette(uiState.background).background,
            modifier = Modifier.padding(padding),
            onTap = onTap,
            onScrollChanged = { _ -> },
            onAnchorChanged = { _ -> },
        )
    }
}

@Composable
private fun BoxScope.ReaderActionRailOverlay(
    visible: Boolean,
    onAppearance: () -> Unit,
    onBookmark: () -> Unit,
    onListen: () -> Unit,
) {
    if (!visible) {
        return
    }
    ActionRail(
        modifier = Modifier.align(Alignment.BottomEnd),
        items = listOf(
            ActionRailItem(
                icon = androidx.compose.material.icons.Icons.Filled.Settings,
                label = "Appearance",
                onClick = { onAppearance() },
            ),
            ActionRailItem(
                icon = androidx.compose.material.icons.Icons.Filled.Edit,
                label = "Bookmark",
                onClick = { onBookmark() },
            ),
            ActionRailItem(
                icon = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                label = "Listen",
                onClick = { onListen() },
            ),
        ),
        mainIcon = androidx.compose.material.icons.Icons.Filled.Settings,
        mainContentDescription = "Reader actions",
        edgePadding = ACTION_RAIL_EDGE_PADDING,
    )
}

@Composable
private fun ReaderAppearanceOverlay(
    showAppearance: Boolean,
    uiState: ReaderUiState,
    readerViewModel: ReaderSessionViewModel,
    onDismiss: () -> Unit,
) {
    if (!showAppearance) {
        return
    }
    val vmAppearance = AppearanceState(
        fontScale = uiState.fontScale,
        lineHeight = uiState.lineHeight,
        background = uiState.background,
        onFontScale = readerViewModel::onFontScaleChange,
        onLineHeight = readerViewModel::onLineHeightChange,
        onBackground = readerViewModel::onBackgroundChange,
    )
    ReaderAppearanceSheet(
        visible = true,
        state = vmAppearance,
        onDismiss = { onDismiss() },
    )
}

//region Additional helpers to shorten ReaderResolvedContent

// Scroll persistence now handled internally by ArticleWebView.

private data class FabState(val visible: Boolean, val showAppearance: Boolean, val confirmExit: Boolean)

@Composable
private fun rememberFabController(): Triple<FabState, () -> Unit, (FabState) -> Unit> {
    val scope = rememberCoroutineScope()
    var fabVisible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun showFabTemporarily() {
        fabVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            kotlinx.coroutines.delay(FAB_HIDE_DELAY_MS)
            fabVisible = false
        }
    }
    DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }
    val state = FabState(fabVisible, showAppearance, confirmExit)
    val setState: (FabState) -> Unit = { s ->
        fabVisible = s.visible
        showAppearance = s.showAppearance
        confirmExit = s.confirmExit
    }
    return Triple(state, ::showFabTemporarily, setState)
}

@Composable
private fun ReaderResolvedContent(
    articleId: String,
    resolvedUrl: String,
    uiState: ReaderUiState,
    onNavigateBack: (() -> Unit)?,
    readerViewModel: ReaderSessionViewModel,
) {
    val (fabState, showFabTemporarily, setFabState) = rememberFabController()
    LaunchedEffect(resolvedUrl) { showFabTemporarily() }

    // Cache CSS per background setting
    val css = loadCssOrBlank(uiState.background)

    BackHandler(enabled = true) {
        when {
            fabState.showAppearance -> {
                setFabState(fabState.copy(showAppearance = false))
            }
            fabState.visible -> {
                setFabState(fabState.copy(visible = false))
            }
            else -> {
                setFabState(fabState.copy(confirmExit = true))
            }
        }
    }

    ReaderResolvedLayout(
        articleId = articleId,
        resolvedUrl = resolvedUrl,
        css = css,
        uiState = uiState,
        fabState = fabState,
        onSetFabState = setFabState,
        onShowFabTemp = showFabTemporarily,
        onNavigateBack = onNavigateBack,
        readerViewModel = readerViewModel,
    )
}

@Composable
private fun ReaderResolvedLayout(
    articleId: String,
    resolvedUrl: String,
    css: String,
    uiState: ReaderUiState,
    fabState: FabState,
    onSetFabState: (FabState) -> Unit,
    onShowFabTemp: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    readerViewModel: ReaderSessionViewModel,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.run { onBackPressedDispatcher }
    Box(Modifier.fillMaxSize()) {
        ReaderWebViewScaffold(
            articleId = articleId,
            resolvedUrl = resolvedUrl,
            css = css,
            uiState = uiState,
            onTap = { onShowFabTemp() },
        )
        ReaderActionRailOverlay(
            visible = fabState.visible,
            onAppearance = { onSetFabState(fabState.copy(showAppearance = true)) },
            onBookmark = { onShowFabTemp() },
            onListen = { onShowFabTemp() },
        )
        ReaderAppearanceOverlay(
            showAppearance = fabState.showAppearance,
            uiState = uiState,
            readerViewModel = readerViewModel,
            onDismiss = { onSetFabState(fabState.copy(showAppearance = false)) },
        )
        if (fabState.confirmExit) {
            ConfirmExitDialog(
                onDismiss = { onSetFabState(fabState.copy(confirmExit = false)) },
                onConfirm = {
                    onSetFabState(fabState.copy(confirmExit = false))
                    onNavigateBack?.invoke() ?: backDispatcher?.onBackPressed()
                },
            )
        }
    }
}

//endregion

//endregion
