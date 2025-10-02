/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.bookmarks

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * High-level navigation entry point for the Bookmarks feature.
 *
 * Obtains a [BookmarksViewModel] via Hilt and delegates rendering to [BookmarksScreen].
 * Exposed as a composable route so that navigation graphs can include the bookmarks
 * feature without duplicating view model retrieval logic.
 *
 * @param vm The injected [BookmarksViewModel]; provided automatically via [hiltViewModel].
 */
@Suppress("FunctionName")
@Composable
fun BookmarksRoute(vm: BookmarksViewModel = hiltViewModel()) {
    BookmarksScreen(vm)
}
