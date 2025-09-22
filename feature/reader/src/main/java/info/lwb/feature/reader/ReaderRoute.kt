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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text

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
    ReaderScreen(
        articleTitle = ui.articleId.ifBlank { "Sample Article" },
        htmlBody = htmlBody,
        settings = settingsState,
        pages = ui.pages,
        currentPageIndex = ui.currentPageIndex,
        onPageChange = vm::onPageChange,
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
        ArticleWebView(url = resolvedUrl)
    } else {
        // As a last resort, try inline HTML if available; otherwise show a minimal error.
        when (contentRes) {
            is info.lwb.core.common.Result.Success -> {
                val data = (contentRes as info.lwb.core.common.Result.Success<info.lwb.core.model.ArticleContent>).data
                ArticleWebView(htmlBody = data.htmlBody, baseUrl = null)
            }
            is info.lwb.core.common.Result.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is info.lwb.core.common.Result.Error -> Box(modifier = Modifier.fillMaxSize()) {
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
