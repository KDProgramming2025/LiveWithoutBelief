/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.AddAnnotationUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** ViewModel exposing small set of dependencies (annotation use case) for the reader UI tree. */
@HiltViewModel
internal class ReaderDeps @Inject constructor(
    private val addAnnotationUseCase: AddAnnotationUseCase,
) : ViewModel() {
    val scope get() = viewModelScope
    suspend fun addAnnotation(articleId: String, start: Int, end: Int, hash: String) =
        addAnnotationUseCase(articleId, start, end, hash)
}

/** Restore previously saved list position if available. */
internal suspend fun restoreListPosition(
    scrollVm: ScrollViewModel,
    articleTitle: String,
    state: LazyListState,
) {
    val index = try {
        scrollVm.observeListIndex(articleTitle).first()
    } catch (_: Throwable) {
        0
    }
    val offset = try {
        scrollVm.observeListOffset(articleTitle).first()
    } catch (_: Throwable) {
        0
    }
    if (index > 0 || offset > 0) {
        state.scrollToItem(index, offset)
    }
}
