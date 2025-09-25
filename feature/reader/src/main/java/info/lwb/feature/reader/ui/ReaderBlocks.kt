/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import info.lwb.feature.reader.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun ParagraphBlock(text: String, query: String, settings: ReaderSettingsState, activeRange: IntRange? = null) {
    val matches = if (query.isBlank()) emptyList() else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { range ->
            if (range.first > lastIndex) append(text.substring(lastIndex, range.first))
            val highlightColor = if (activeRange == range) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            withStyle(MaterialTheme.typography.bodyLarge.toSpanStyle().copy(background = highlightColor)) { append(text.substring(range)) }
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
fun HeadingBlock(level: Int, text: String) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(text, style = style)
}

@Composable
fun AudioBlock(url: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) { AudioPlayer(url) }
}

@Composable
fun AudioPlayer(url: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.fromUri(url)
            setMediaItem(item)
            prepare()
            addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        ready = playbackState != androidx.media3.common.Player.STATE_BUFFERING && playbackState != androidx.media3.common.Player.STATE_IDLE
                    }
                },
            )
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!ready) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = {
            if (player.isPlaying) { player.pause(); isPlaying = false } else { player.play(); isPlaying = true }
        }) { Icon(Icons.Filled.PlayArrow, contentDescription = if (player.isPlaying) "Pause audio" else "Play audio") }
        Spacer(Modifier.width(8.dp))
        Text(text = url.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun YouTubeBlock(videoId: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        isLongClickable = false
                        setOnLongClickListener { true }
                        setDownloadListener { _, _, _, _, _ -> }
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        setOnScrollChangeListener { v, scrollX, scrollY, _, _ -> if (scrollX != 0) v.scrollTo(0, scrollY) }
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                val u = url ?: return false
                                return try { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u)); context.startActivity(intent); true } catch (_: Throwable) { false }
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (request?.isForMainFrame == true) {
                                    return try { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u)); context.startActivity(intent); true } catch (_: Throwable) { false }
                                }
                                return false
                            }
                        }
                        loadUrl("https://www.youtube.com/embed/$videoId")
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            val context = LocalContext.current
            Button(
                onClick = {
                    val target = "https://www.youtube.com/watch?v=$videoId"
                    try { val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)); context.startActivity(intent) } catch (_: Throwable) {}
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) { Text("Watch this video on YouTube") }
        }
    }
}
