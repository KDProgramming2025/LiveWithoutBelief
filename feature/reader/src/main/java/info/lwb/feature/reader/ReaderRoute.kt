/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import info.lwb.feature.reader.ReaderSettingsRepository
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
fun ReaderByIdRoute(
    articleId: String,
    onNavigateBack: (() -> Unit)? = null,
) {
    val svcVm: info.lwb.feature.reader.viewmodels.ReaderViewModel = hiltViewModel()
    LaunchedEffect(articleId) { svcVm.loadArticleContent(articleId) }
    val contentRes by svcVm.articleContent.collectAsState()
    val articlesRes by svcVm.articles.collectAsState()
    val env: ReaderEnv = hiltViewModel()
    val vm: ReaderViewModel = hiltViewModel()
    val ui by vm.uiState.collectAsState()

    val resolvedUrl = remember(contentRes, articlesRes, env.apiBaseUrl, articleId) {
        resolveArticleUrl(articleId, contentRes, articlesRes, env.apiBaseUrl)
    }

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
}

// Minimal Hilt VM to surface environment strings
@HiltViewModel
internal class ReaderEnv @Inject constructor(@Named("apiBaseUrl") val apiBaseUrl: String) : ViewModel()

//region Helpers & extracted composables

private fun resolveArticleUrl(
    articleId: String,
    contentRes: Result<ArticleContent>?,
    articlesRes: Result<List<Article>>?,
    apiBaseUrl: String,
): String? {
    val viaContent = (contentRes as? Result.Success<ArticleContent>)?.data?.indexUrl
    if (!viaContent.isNullOrBlank()) {
        return viaContent
    }
    val list = (articlesRes as? Result.Success<List<Article>>)?.data
    val slug = list?.firstOrNull { it.id == articleId }?.slug
    if (slug.isNullOrBlank()) {
        return null
    }
    val api = apiBaseUrl.trimEnd('/')
    val adminBase = api.replace(Regex("/API/?$"), "/Admin/").trimEnd('/')
    return "$adminBase/web/articles/$slug/"
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
    resolvedUrl: String,
    css: String,
    uiState: ReaderUiState,
    initialScroll: Int,
    initialAnchor: String?,
    onTap: () -> Unit,
    onScrollSave: (Int) -> Unit,
    onAnchorSave: (String) -> Unit,
) {
    Scaffold { padding ->
        ArticleWebView(
            url = resolvedUrl,
            injectedCss = css,
            fontScale = uiState.fontScale.toFloat(),
            lineHeight = uiState.lineHeight.toFloat(),
            backgroundColor = readerPalette(uiState.background).background,
            modifier = Modifier.padding(padding),
            onTap = onTap,
            initialScrollY = initialScroll,
            initialAnchor = initialAnchor?.takeIf { it.isNotBlank() },
            onScrollChanged = { y -> onScrollSave(y) },
            onAnchorChanged = { a -> onAnchorSave(a) },
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
    readerViewModel: ReaderViewModel,
    onDismiss: () -> Unit
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

private data class ScrollRestore(val initialScroll: Int, val initialAnchor: String?)

@Composable
private fun rememberScrollRestore(articleId: String): ScrollRestore {
    val scrollVm: ScrollViewModel = hiltViewModel()
    var initialScroll by remember { mutableStateOf(0) }
    var initialAnchor by remember { mutableStateOf("") }
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
    return ScrollRestore(initialScroll, initialAnchor.takeIf { it.isNotBlank() })
}

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
    readerViewModel: ReaderViewModel
) {
    val (fabState, showFabTemporarily, setFabState) = rememberFabController()
    LaunchedEffect(resolvedUrl) { showFabTemporarily() }

    val scrollRestore = rememberScrollRestore(articleId)
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
        scrollRestore = scrollRestore,
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
    scrollRestore: ScrollRestore,
    fabState: FabState,
    onSetFabState: (FabState) -> Unit,
    onShowFabTemp: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    readerViewModel: ReaderViewModel,
) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // Obtain once inside composition; do not call inside non-composable lambdas
    val scrollVm: ScrollViewModel = hiltViewModel()
    Box(Modifier.fillMaxSize()) {
        ReaderWebViewScaffold(
            resolvedUrl = resolvedUrl,
            css = css,
            uiState = uiState,
            initialScroll = scrollRestore.initialScroll,
            initialAnchor = scrollRestore.initialAnchor,
            onTap = { onShowFabTemp() },
            onScrollSave = { y -> scope.launch { scrollVm.save(articleId, y) } },
            onAnchorSave = { a -> scope.launch { scrollVm.saveAnchor(articleId, a) } },
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
