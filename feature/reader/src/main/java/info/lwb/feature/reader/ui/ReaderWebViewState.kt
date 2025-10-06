package info.lwb.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Provides a remembered [ArticleWebState] keyed by url + html body content.
 * Split into its own file to work around a detekt crash in large composite source files.
 */
@Composable
internal fun rememberArticleWebState(url: String?, htmlBody: String?): ArticleWebState {
    val ready = remember(key1 = url, key2 = htmlBody) { mutableStateOf(false) }
    val firstLoad = remember(key1 = url, key2 = htmlBody) { mutableStateOf(true) }
    val lastFontScale = remember(key1 = url, key2 = htmlBody) { mutableStateOf<Float?>(null) }
    val lastLineHeight = remember(key1 = url, key2 = htmlBody) { mutableStateOf<Float?>(null) }
    val restoreActive = remember(key1 = url, key2 = htmlBody) { mutableStateOf(false) }
    return ArticleWebState(
        ready = ready,
        firstLoad = firstLoad,
        lastFontScale = lastFontScale,
        lastLineHeight = lastLineHeight,
        restoreActive = restoreActive,
    )
}
