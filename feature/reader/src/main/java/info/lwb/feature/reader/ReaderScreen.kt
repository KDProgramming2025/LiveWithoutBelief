package info.lwb.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ReaderScreen(
    articleTitle: String,
    htmlBody: String,
    settings: ReaderSettingsState,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(htmlBody) { parseHtmlToBlocks(htmlBody) }
    var searchQuery by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            SmallTopAppBar(title = { Text(articleTitle) })
        },
        bottomBar = {
            ReaderControlsBar(settings = settings) { font, line ->
                settings.onFontScaleChange(font)
                settings.onLineHeightChange(line)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchBar(searchQuery) { searchQuery = it }
            LazyColumn(Modifier.fillMaxSize()) {
                items(blocks.size) { idx ->
                    when (val b = blocks[idx]) {
                        is ContentBlock.Paragraph -> ParagraphBlock(b.text, searchQuery, settings)
                        is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                        is ContentBlock.Image -> AsyncImage(model = b.url, contentDescription = b.alt, modifier = Modifier.fillMaxWidth().height(220.dp))
                        is ContentBlock.Audio -> AudioBlock(b.url)
                        is ContentBlock.YouTube -> YouTubeBlock(b.videoId)
                    }
                    Spacer(Modifier.height(12.dp))
            Text("YouTube: $videoId", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReaderControlsBar(settings: ReaderSettingsState, onChange: (Double, Double) -> Unit) {
    Surface(shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text("Font: ${"%.2f".format(settings.fontScale)}  Line: ${"%.2f".format(settings.lineHeight)}", style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontScale.toFloat(),
                    onValueChange = { onChange(it.toDouble().coerceIn(0.8, 1.6), settings.lineHeight) },
                    valueRange = 0.8f..1.6f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = settings.lineHeight.toFloat(),
                    onValueChange = { onChange(settings.fontScale, it.toDouble().coerceIn(1.0, 2.0)) },
                    valueRange = 1.0f..2.0f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search in article") }
    )
}/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.model.Article
import info.lwb.feature.reader.viewmodels.ReaderViewModel

@Suppress("FunctionName") // Compose convention allows PascalCase composable names; suppress ktlint rule.
@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val articles by viewModel.articles.collectAsState()
    val articleContent by viewModel.articleContent.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.refreshArticles() }) {
            Text("Refresh Articles")
        }

        when (articles) {
            is Result.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Result.Success -> {
                val articleList = (articles as Result.Success<List<Article>>).data
                LazyColumn {
                    items(articleList) { article ->
                        Text(
                            text = article.title,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            is Result.Error -> {
                Text("Error: ${(articles as Result.Error).throwable.message}")
            }
        }
    }
}
