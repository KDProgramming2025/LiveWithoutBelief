/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import info.lwb.feature.reader.ReaderSettingsState

// region constants -------------------------------------------------------------------------------------------------
private const val MIN_FONT_SIZE = 10
private const val MIN_LINE_HEIGHT = 12
private const val SPACING_XS_UNITS = 4
private const val SPACING_SM_UNITS = 8
private const val SPACING_MD_UNITS = 12

// YouTube embed player typical 16:9 area height when width ~360dp -> 360 * 9 / 16 = 202.5 ≈ 200
private const val YT_PLAYER_HEIGHT_UNITS = 200
private const val TONAL_ELEVATION_SM_UNITS = 2
private const val PADDING_BLOCK_UNITS = 12
private const val ICON_PROGRESS_SIZE_UNITS = 24
private const val INDEX_INCREMENT = 1
private const val ZERO = 0
private const val HEADING_LEVEL_MEDIUM = 1
private const val HEADING_LEVEL_SMALL = 2
private const val HEADING_LEVEL_TITLE = 3
private const val ALPHA_PERCENT_DENOMINATOR = 100
private const val HIGHLIGHT_ACTIVE_ALPHA_NUMERATOR = 55
private const val HIGHLIGHT_MATCH_ALPHA_NUMERATOR = 30
private val HIGHLIGHT_ACTIVE_ALPHA: Float = HIGHLIGHT_ACTIVE_ALPHA_NUMERATOR / ALPHA_PERCENT_DENOMINATOR.toFloat()
private val HIGHLIGHT_MATCH_ALPHA: Float = HIGHLIGHT_MATCH_ALPHA_NUMERATOR / ALPHA_PERCENT_DENOMINATOR.toFloat()

private val MIN_FONT_SIZE_SP = MIN_FONT_SIZE.sp
private val MIN_LINE_HEIGHT_SP = MIN_LINE_HEIGHT.sp
private val SPACING_XS = SPACING_XS_UNITS.dp
private val SPACING_SM = SPACING_SM_UNITS.dp
private val SPACING_MD = SPACING_MD_UNITS.dp
private val YT_PLAYER_HEIGHT = YT_PLAYER_HEIGHT_UNITS.dp
private val TONAL_ELEVATION_SM = TONAL_ELEVATION_SM_UNITS.dp
private val PADDING_BLOCK = PADDING_BLOCK_UNITS.dp
private val ICON_PROGRESS_SIZE = ICON_PROGRESS_SIZE_UNITS.dp

// endregion --------------------------------------------------------------------------------------------------------

@Composable
internal fun ParagraphBlock(
    text: String,
    query: String,
    settings: ReaderSettingsState,
    activeRange: IntRange? = null,
) {
    val matches = if (query.isBlank()) {
        emptyList()
    } else {
        val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        val collected = mutableListOf<IntRange>()
        for (m in regex.findAll(text)) {
            collected.add(m.range)
        }
        collected
    }
    // Base text style used for paragraph spans.
    val baseStyle = MaterialTheme.typography.bodyLarge
    val annotated = buildAnnotatedString {
        var lastIndex = ZERO
        matches.forEach { range ->
            if (range.first > lastIndex) {
                append(text.substring(lastIndex, range.first))
            }
            val highlightColor = if (activeRange == range) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = HIGHLIGHT_ACTIVE_ALPHA)
            } else {
                MaterialTheme.colorScheme.secondary.copy(alpha = HIGHLIGHT_MATCH_ALPHA)
            }
            val styleSpan = baseStyle
                .toSpanStyle()
            val highlightSpan = styleSpan
                .copy(background = highlightColor)
            withStyle(highlightSpan) {
                append(text.substring(range))
            }
            lastIndex = range.last + INDEX_INCREMENT
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    val fontSizeValue = baseStyle.fontSize.value
    val rawFontSize = fontSizeValue * settings.fontScale.toFloat()
    val scaledFont = rawFontSize
        .coerceAtLeast(MIN_FONT_SIZE_SP.value)
        .sp
    val lineHeightValue = baseStyle.lineHeight.value
    val rawLineHeight = lineHeightValue * settings.lineHeight.toFloat()
    val scaledLineHeight = rawLineHeight
        .coerceAtLeast(MIN_LINE_HEIGHT_SP.value)
        .sp
    Text(
        annotated,
        style = baseStyle.copy(
            fontSize = scaledFont,
            lineHeight = scaledLineHeight,
        ),
    )
}

@Composable
internal fun HeadingBlock(level: Int, text: String) {
    val style = when (level) {
        HEADING_LEVEL_MEDIUM -> {
            MaterialTheme.typography.headlineMedium
        }
        HEADING_LEVEL_SMALL -> {
            MaterialTheme.typography.headlineSmall
        }
        HEADING_LEVEL_TITLE -> {
            MaterialTheme.typography.titleLarge
        }
        else -> {
            MaterialTheme.typography.titleMedium
        }
    }
    Text(text, style = style)
}

@Composable
internal fun AudioBlock(url: String) {
    Surface(
        tonalElevation = TONAL_ELEVATION_SM,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AudioPlayer(url)
    }
}

@Composable
internal fun AudioPlayer(url: String) {
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
                        val buffering = androidx.media3.common.Player.STATE_BUFFERING
                        val idle = androidx.media3.common.Player.STATE_IDLE
                        ready = playbackState != buffering && playbackState != idle
                    }
                },
            )
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Row(Modifier.padding(PADDING_BLOCK), verticalAlignment = Alignment.CenterVertically) {
        if (!ready) {
            CircularProgressIndicator(modifier = Modifier.size(ICON_PROGRESS_SIZE))
            Spacer(Modifier.width(SPACING_MD))
        }
        IconButton(
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.play()
                    isPlaying = true
                }
            },
        ) {
            val contentDesc = if (player.isPlaying) {
                "Pause audio"
            } else {
                "Play audio"
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = contentDesc,
            )
        }
        Spacer(Modifier.width(SPACING_SM))
        Text(text = url.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun YouTubeBlock(videoId: String) {
    Surface(
        tonalElevation = TONAL_ELEVATION_SM,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            YouTubePlayerView(videoId = videoId)
            Spacer(Modifier.height(SPACING_SM))
            YouTubeLaunchButton(videoId = videoId)
        }
    }
}

@Composable
private fun YouTubePlayerView(videoId: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(YT_PLAYER_HEIGHT),
        factory = { ctx ->
            WebView(ctx).apply {
                configureYouTubeWebView(this, videoId)
            }
        },
    )
}

@Composable
private fun YouTubeLaunchButton(videoId: String) {
    val context = LocalContext.current
    val target = remember(videoId) { "https://www.youtube.com/watch?v=$videoId" }
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )
    Button(
        onClick = {
            launchExternalUrl(context, target)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_XS),
        colors = buttonColors,
    ) {
        Text("Watch this video on YouTube")
    }
}

private fun configureYouTubeWebView(webView: WebView, videoId: String) {
    with(webView) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Removed deprecated databaseEnabled usage.
        isLongClickable = false
        setOnLongClickListener { true }
        setDownloadListener { _, _, _, _, _ -> }
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setOnScrollChangeListener { v, scrollX, scrollY, _, _ ->
            if (scrollX != ZERO) {
                v.scrollTo(ZERO, scrollY)
            }
        }
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewClient = ReaderYouTubeClient(context)
        loadUrl("https://www.youtube.com/embed/$videoId")
    }
}

private class ReaderYouTubeClient(private val ctx: android.content.Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val target = url ?: return false
        return launchIntent(target)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
        val u = request?.url?.toString() ?: return false
        return if (request.isForMainFrame) {
            launchIntent(u)
        } else {
            false
        }
    }

    private fun launchIntent(u: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

private fun launchExternalUrl(context: android.content.Context, target: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
        context.startActivity(intent)
    } catch (_: Throwable) {
        // ignored
    }
}
