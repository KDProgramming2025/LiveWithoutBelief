/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ViewModel facade over [ScrollRepository] exposing scroll, anchor, and list position persistence APIs.
 * Keeps UI layer unaware of underlying storage implementation.
 */
@HiltViewModel
internal class ScrollViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val scrollRepository: ScrollRepository = ScrollRepository(context = context)

    /** Observe vertical scroll Y position for an article. */
    fun observe(articleId: String): Flow<Int> = scrollRepository.observe(articleId)

    /** Persist latest vertical scroll Y position for an article. */
    suspend fun save(articleId: String, y: Int) = scrollRepository.save(articleId = articleId, scrollY = y)

    /** Observe anchor id (e.g., element id) near current position. */
    fun observeAnchor(articleId: String): Flow<String> = scrollRepository.observeAnchor(articleId)

    /** Persist anchor id near current position (nullable to clear). */
    suspend fun saveAnchor(articleId: String, anchor: String?) = scrollRepository.saveAnchor(
        articleId = articleId,
        anchor = anchor,
    )

    /** Observe list index of currently visible item. */
    fun observeListIndex(articleId: String): Flow<Int> = scrollRepository.observeListIndex(articleId)

    /** Observe pixel offset within the currently visible list item. */
    fun observeListOffset(articleId: String): Flow<Int> = scrollRepository.observeListOffset(articleId)

    /** Persist list index + offset snapshot for current position. */
    suspend fun saveList(articleId: String, index: Int, offset: Int) = scrollRepository.saveList(
        articleId = articleId,
        index = index,
        offset = offset,
    )
}
