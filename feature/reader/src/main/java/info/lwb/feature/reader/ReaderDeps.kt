/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.AddAnnotationUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * ViewModel exposing small set of dependencies (annotation use case) for the reader UI tree.
 */
@HiltViewModel
internal class ReaderDeps @Inject constructor(private val addAnnotationUseCase: AddAnnotationUseCase) : ViewModel() {
    val scope get() = viewModelScope

    // Suspend wrapper passes through to underlying suspend use case to preserve structured concurrency at call sites.
    suspend fun addAnnotation(articleId: String, start: Int, end: Int, hash: String) =
        addAnnotationUseCase(
            articleId = articleId,
            startOffset = start,
            endOffset = end,
            anchorHash = hash,
        )
}

/** Restore previously saved list position if available. */
internal suspend fun restoreListPosition(scrollVm: ScrollViewModel, articleTitle: String, state: LazyListState) {
    val index = try {
        scrollVm.observeListIndex(articleTitle).first()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (_: java.io.IOException) {
        0
    } catch (_: IllegalStateException) {
        0
    }
    val offset = try {
        scrollVm.observeListOffset(articleTitle).first()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (_: java.io.IOException) {
        0
    } catch (_: IllegalStateException) {
        0
    }
    if (index > 0 || offset > 0) {
        state.scrollToItem(index, offset)
    }
}
