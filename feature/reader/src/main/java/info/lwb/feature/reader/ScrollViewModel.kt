/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

@HiltViewModel
class ScrollViewModel @Inject constructor(@ApplicationContext appContext: Context) : ViewModel() {
    private val repo = ScrollRepository(appContext)
    fun observe(articleId: String): Flow<Int> = repo.observe(articleId)
    suspend fun save(articleId: String, y: Int) = repo.save(articleId, y)
    fun observeAnchor(articleId: String): Flow<String> = repo.observeAnchor(articleId)
    suspend fun saveAnchor(articleId: String, anchor: String?) = repo.saveAnchor(articleId, anchor)
    fun observeListIndex(articleId: String): Flow<Int> = repo.observeListIndex(articleId)
    fun observeListOffset(articleId: String): Flow<Int> = repo.observeListOffset(articleId)
    suspend fun saveList(articleId: String, index: Int, offset: Int) = repo.saveList(articleId, index, offset)
}
