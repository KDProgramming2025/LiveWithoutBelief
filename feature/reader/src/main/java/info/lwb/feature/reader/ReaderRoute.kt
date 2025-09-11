package info.lwb.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

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

private val SAMPLE_HTML = """
    <h1>Sample Article Heading</h1>
    <p>This is a sample paragraph used for initial reader validation before the ingestion pipeline is active.</p>
    <p>Adjust font size and line spacing using the controls below. Use search to highlight terms like sample.</p>
    <img src="https://placekitten.com/800/400" alt="Kitten" />
    <audio src="https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"></audio>
    <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
""".trimIndent()
