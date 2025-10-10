package info.lwb.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Provides a remembered [ArticleWebState] keyed by remote url .
 */
@Composable
internal fun rememberArticleWebState(url: String?): ArticleWebState {
    val ready = remember(key1 = url) { mutableStateOf(false) }
    val firstLoad = remember(key1 = url) { mutableStateOf(true) }
    val lastFontScale = remember(key1 = url) { mutableStateOf<Float?>(null) }
    val lastLineHeight = remember(key1 = url) { mutableStateOf<Float?>(null) }
    val restoreActive = remember(key1 = url) { mutableStateOf(false) }
    return ArticleWebState(
        ready = ready,
        firstLoad = firstLoad,
        lastFontScale = lastFontScale,
        lastLineHeight = lastLineHeight,
        restoreActive = restoreActive,
    )
}
