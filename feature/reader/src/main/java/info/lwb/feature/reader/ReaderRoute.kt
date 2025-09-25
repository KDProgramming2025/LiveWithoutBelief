/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
import info.lwb.feature.reader.ui.ActionRail
import info.lwb.feature.reader.ui.ActionRailItem
import info.lwb.feature.reader.ui.ArticleWebView
import info.lwb.feature.reader.ui.readerPalette
import info.lwb.feature.reader.ui.themeCssAssetPath
import info.lwb.feature.reader.ui.loadAssetText
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewmodel.compose.viewModel
import info.lwb.feature.reader.tts.ReaderTtsViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

// Inline html-based ReaderRoute has been removed; use ReaderByIdRoute(articleId) exclusively.

/**
 * Route that loads the article content by id from the repository and forwards to ReaderScreen.
 */
@Composable
fun ReaderByIdRoute(
    articleId: String,
    onNavigateBack: (() -> Unit)? = null,
    vm: ReaderViewModel = hiltViewModel(),
) {
    // For MVP, fetch content using GetArticleContentUseCase via the feature.viewmodels.ReaderViewModel
    val svcVm: info.lwb.feature.reader.viewmodels.ReaderViewModel = hiltViewModel()
    androidx.compose.runtime.LaunchedEffect(articleId) { svcVm.loadArticleContent(articleId) }
    val contentRes by svcVm.articleContent.collectAsState()
    val ui by vm.uiState.collectAsState()
    val articlesRes by svcVm.articles.collectAsState()
    val env: ReaderEnv = hiltViewModel()
    // Resolve a URL to load: prefer server-provided indexUrl, else compute from slug using Admin web base derived from API base.
    val resolvedUrl: String? = remember(contentRes, articlesRes, env.apiBaseUrl, articleId) {
        val viaContent = (contentRes as? info.lwb.core.common.Result.Success<info.lwb.core.model.ArticleContent>)?.data?.indexUrl
        if (!viaContent.isNullOrBlank()) return@remember viaContent
        val list = (articlesRes as? info.lwb.core.common.Result.Success<List<info.lwb.core.model.Article>>)?.data
        val slug = list?.firstOrNull { it.id == articleId }?.slug
        if (!slug.isNullOrBlank()) {
            val api = env.apiBaseUrl.trimEnd('/')
            val adminBase = api.replace(Regex("/API/?$"), "/Admin/").trimEnd('/')
            "$adminBase/web/articles/$slug/"
        } else null
    }

    if (!resolvedUrl.isNullOrBlank()) {
        // When rendering server URL in a WebView, we still want the FAB behavior.
        var fabVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var hideJob by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var confirmExit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        fun showFabTemporarily() {
            fabVisible = true
            hideJob?.cancel()
            hideJob = scope.launch {
                kotlinx.coroutines.delay(5000)
                fabVisible = false
            }
        }
        androidx.compose.runtime.LaunchedEffect(resolvedUrl) { showFabTemporarily() }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }
        var showAppearance by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ttsVm: ReaderTtsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var webRef by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.webkit.WebView?>(null) }
    var pageReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var speaking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val ctx = LocalContext.current
        androidx.activity.compose.BackHandler(enabled = true) {
            when {
                showAppearance -> showAppearance = false
                fabVisible -> fabVisible = false
                else -> confirmExit = true
            }
        }
        val scrollVm: ScrollViewModel = hiltViewModel()
        var initialScroll by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
        var initialAnchor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        // Load initial scroll value once
        androidx.compose.runtime.LaunchedEffect(articleId) {
            initialScroll = try { scrollVm.observe(articleId).first() } catch (_: Throwable) { 0 }
            initialAnchor = try { scrollVm.observeAnchor(articleId).first() } catch (_: Throwable) { "" }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            val css = run {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val path = themeCssAssetPath(readerPalette(ui.background))
                try { loadAssetText(ctx, path) } catch (_: Throwable) { "" }
            }
            androidx.compose.material3.Scaffold { padding ->
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
                    onAnchorChanged = { a -> scope.launch { scrollVm.saveAnchor(articleId, a) } }
                    , onWebViewReady = { wv -> webRef = wv }
                    , onPageReady = { pageReady = true }
                )
            }
            if (fabVisible) {
                ActionRail(
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
                    items = listOf(
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.Settings,
                            label = "Appearance",
                            onClick = { showAppearance = true }
                        ),
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.Edit,
                            label = "Bookmark",
                            onClick = { showFabTemporarily() }
                        ),
                        ActionRailItem(
                            icon = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                            label = "Listen",
                            onClick = {
                                showFabTemporarily()
                                if (!speaking) {
                                    if (webRef == null || !pageReady) {
                                        Toast.makeText(ctx, "Page is still loading", Toast.LENGTH_SHORT).show()
                                        return@ActionRailItem
                                    }
                                    // Get article text via JS and speak
                                    val wv = webRef
                                    if (wv != null) {
                                        ttsVm.ensureReady { ok ->
                                            if (ok) {
                                                try {
                                                    wv.evaluateJavascript("(window.lwbGetArticleText && window.lwbGetArticleText())||''") { res ->
                                                        val text = res?.let { runCatching {
                                                            // Result is a JSON-encoded string from WebView; parse safely
                                                            org.json.JSONArray("[" + it + "]").getString(0)
                                                        }.getOrNull() }?.replace("\r", "") ?: ""
                                                        if (text.isBlank()) {
                                                            Toast.makeText(ctx, "Nothing to read on this page", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            speaking = true
                                                            ttsVm.speak(text)
                                                        }
                                                    }
                                                } catch (_: Throwable) { Toast.makeText(ctx, "Could not extract text", Toast.LENGTH_SHORT).show() }
                                            }
                                        }
                                    }
                                } else {
                                    ttsVm.stop(); speaking = false
                                }
                            }
                        ),
                    ),
                    mainIcon = androidx.compose.material.icons.Icons.Filled.Settings,
                    mainContentDescription = "Reader actions",
                    edgePadding = 16.dp,
                )
            }
            if (showAppearance) {
                val vmAppearance = AppearanceState(
                    fontScale =  ui.fontScale,
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
                val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { androidx.compose.material3.Text("Leave reader?") },
                    text = { androidx.compose.material3.Text("Are you sure you want to exit the reader?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            confirmExit = false
                            // Prefer explicit navigation callback to avoid BackHandler loop
                            onNavigateBack?.invoke() ?: backDispatcher?.onBackPressed()
                        }) { androidx.compose.material3.Text("Exit") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { androidx.compose.material3.Text("Cancel") }
                    }
                )
            }
        }
    } else {
        // If URL cannot be resolved, show a minimal load/error placeholder without attempting inline fallback.
        when (contentRes) {
            is info.lwb.core.common.Result.Loading -> Box(modifier = Modifier.fillMaxSize()) {
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
