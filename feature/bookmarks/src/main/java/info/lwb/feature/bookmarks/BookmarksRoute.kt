/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.bookmarks

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Suppress("FunctionName")
@Composable
fun BookmarksRoute(vm: BookmarksViewModel = hiltViewModel()) {
    BookmarksScreen(vm)
}
