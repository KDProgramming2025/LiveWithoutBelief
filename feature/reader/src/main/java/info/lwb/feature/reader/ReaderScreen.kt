package info.lwb.feature.reader

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Data holder for reader settings provided by caller (ViewModel layer wires flows & mutations)
data class ReaderSettingsState(
    val fontScale: Double,
    val lineHeight: Double,
    val onFontScaleChange: (Double) -> Unit,
    val onLineHeightChange: (Double) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    articleTitle: String,
    htmlBody: String,
    settings: ReaderSettingsState,
    pages: List<Page>? = null,
    currentPageIndex: Int = 0,
    onPageChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val blocks = remember(htmlBody) { parseHtmlToBlocks(htmlBody) }
    var searchQuery by remember { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
    topBar = { TopAppBar(title = { Text(articleTitle) }) },
        bottomBar = {
            ReaderControlsBar(settings = settings) { font, line ->
                settings.onFontScaleChange(font)
                settings.onLineHeightChange(line)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchBar(searchQuery) { searchQuery = it }
            val pageList = pages
            if (pageList != null && pageList.size > 1) {
                // Simple horizontal pager substitute using Row + manual buttons (avoid new dependency)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Page ${currentPageIndex + 1} / ${pageList.size}", style = MaterialTheme.typography.labelMedium)
                    Row {
                        androidx.compose.material3.Button(enabled = currentPageIndex > 0, onClick = { onPageChange((currentPageIndex - 1).coerceAtLeast(0)) }) { Text("Prev") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Button(enabled = currentPageIndex < pageList.lastIndex, onClick = { onPageChange((currentPageIndex + 1).coerceAtMost(pageList.lastIndex)) }) { Text("Next") }
                    }
                }
                val pageBlocks = remember(currentPageIndex, pageList) { pageList.getOrNull(currentPageIndex)?.blocks ?: emptyList() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(pageBlocks.size) { idx ->
                        val b = pageBlocks[idx]
                        when (b) {
                            is ContentBlock.Paragraph -> ParagraphBlock(b.text, searchQuery, settings)
                            is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                            is ContentBlock.Image -> AsyncImage(model = b.url, contentDescription = b.alt, modifier = Modifier.fillMaxWidth().height(220.dp))
                            is ContentBlock.Audio -> AudioBlock(b.url)
                            is ContentBlock.YouTube -> YouTubeBlock(b.videoId)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(blocks.size) { idx ->
                        val b = blocks[idx]
                        when (b) {
                            is ContentBlock.Paragraph -> ParagraphBlock(b.text, searchQuery, settings)
                            is ContentBlock.Heading -> HeadingBlock(b.level, b.text)
                            is ContentBlock.Image -> AsyncImage(model = b.url, contentDescription = b.alt, modifier = Modifier.fillMaxWidth().height(220.dp))
                            is ContentBlock.Audio -> AudioBlock(b.url)
                            is ContentBlock.YouTube -> YouTubeBlock(b.videoId)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphBlock(text: String, query: String, settings: ReaderSettingsState) {
    val matches = if (query.isBlank()) emptyList() else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { range ->
            if (range.first > lastIndex) append(text.substring(lastIndex, range.first))
            withStyle(MaterialTheme.typography.bodyLarge.toSpanStyle().copy(background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))) {
                append(text.substring(range))
            }
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
    val baseStyle = MaterialTheme.typography.bodyLarge
    val scaledFont = (baseStyle.fontSize.value * settings.fontScale).coerceAtLeast(10.0).sp
    val scaledLineHeight = (baseStyle.lineHeight.value * settings.lineHeight).coerceAtLeast(12.0).sp
    Text(annotated, style = baseStyle.copy(fontSize = scaledFont, lineHeight = scaledLineHeight))
}

@Composable
private fun HeadingBlock(level: Int, text: String) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(text, style = style)
}

@Composable
private fun AudioBlock(url: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Audio: $url", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun YouTubeBlock(videoId: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
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
}
