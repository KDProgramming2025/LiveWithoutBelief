/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.log.Logger
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "ReaderRoute"

// Inline html-based ReaderRoute has been removed; use ReaderByIdRoute(articleId) exclusively.

/**
 * Route that loads the article content by id, resolves the server WebView URL and renders the
 * reader experience (web content + action rail + appearance sheet + exit dialog).
 *
 * High level orchestration only; heavy UI logic is split into smaller composables to keep
 * complexity low and satisfy Detekt rules.
 */
@Composable
fun ReaderByIdRoute(
    articleId: String,
    navIndexUrl: String?,
    onNavigateBack: (() -> Unit)? = null,
) {
    val url = navIndexUrl
    if (url.isNullOrBlank()) {
        // Nothing to render; backend must always supply indexUrl via navigation.
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }
    val vm: ReaderSessionViewModel = hiltViewModel()
    LaunchedEffect(Unit) { Logger.d(TAG) { "Entered ReaderByIdRoute (navUrl) articleId=" + articleId } }
    // Record active article id for appearance persistence (content body not used).
    LaunchedEffect(articleId) { vm.loadArticle(articleId, "") }
    Logger.d(TAG) { "Using navIndexUrl articleId=" + articleId + " url=" + url }
    ReaderIndexScreen(url = url, vm = vm, onNavigateBack = onNavigateBack)
}

// Minimal Hilt VM to surface environment strings
@HiltViewModel
internal class ReaderEnv @Inject constructor(@Named("apiBaseUrl") val apiBaseUrl: String) : ViewModel()
