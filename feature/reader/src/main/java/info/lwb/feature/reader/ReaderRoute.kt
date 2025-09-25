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

/**
 * Entry point composable for the Reader feature wiring the ViewModel (Hilt) to the UI layer.
 * For now it loads a temporary hard-coded sample article until ingestion pipeline supplies real data.
 */
@Composable
fun ReaderRoute(
    articleId: String = "sample-1",
    htmlBody: String = SAMPLE_HTML,
    vm: ReaderViewModel = hiltViewModel(),
) {
    // Load article only once per id change
    androidx.compose.runtime.LaunchedEffect(articleId, htmlBody) {
        vm.loadArticle(articleId, htmlBody)
    }
    val ui by vm.uiState.collectAsState()
    val settingsState = ReaderSettingsState(
        fontScale = ui.fontScale,
        lineHeight = ui.lineHeight,
        onFontScaleChange = vm::onFontScaleChange,
        onLineHeightChange = vm::onLineHeightChange,
    )
    val appearance = AppearanceState(
        fontScale = ui.fontScale,
        lineHeight = ui.lineHeight,
        background = ui.background,
        onFontScale = vm::onFontScaleChange,
        onLineHeight = vm::onLineHeightChange,
        onBackground = vm::onBackgroundChange,
    )
    val palette = readerPalette(ui.background)
    val injectedCss = run {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val path = themeCssAssetPath(palette)
        try { loadAssetText(ctx, path) } catch (_: Throwable) { "" }
    }
    ReaderScreen(
        articleTitle = ui.articleId.ifBlank { "Sample Article" },
        htmlBody = htmlBody,
        settings = settingsState,
        appearance = appearance,
        pages = ui.pages,
        currentPageIndex = ui.currentPageIndex,
        onPageChange = vm::onPageChange,
        injectedCss = injectedCss,
    )
}

/**
 * Route that loads the article content by id from the repository and forwards to ReaderScreen.
 */
@Composable
fun ReaderByIdRoute(articleId: String, vm: ReaderViewModel = hiltViewModel()) {
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
                    onTap = { showFabTemporarily() }
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
                            onClick = { showFabTemporarily() }
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

private val SAMPLE_HTML = """
    <h1>Sample Article Heading</h1>
    <p>This is a sample paragraph used for initial reader validation before the ingestion pipeline is active.</p>
    <p>Adjust font size and line spacing using the controls below. Use search to highlight terms like sample.</p>
    <img src="https://placekitten.com/800/400" alt="Kitten" />
    <audio src="https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"></audio>
    <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
""".trimIndent()
