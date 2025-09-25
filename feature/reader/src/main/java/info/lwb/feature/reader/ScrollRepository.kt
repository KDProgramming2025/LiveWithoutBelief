/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.scrollStore by preferencesDataStore(name = "reader_scroll")

class ScrollRepository(private val context: Context) {
    private fun key(articleId: String) = intPreferencesKey("scroll_${'$'}articleId")
    private fun keyIndex(articleId: String) = intPreferencesKey("list_index_${'$'}articleId")
    private fun keyOffset(articleId: String) = intPreferencesKey("list_offset_${'$'}articleId")

    fun observe(articleId: String): Flow<Int> = context.scrollStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[key(articleId)] ?: 0 }

    suspend fun save(articleId: String, scrollY: Int) {
        context.scrollStore.edit { prefs -> prefs[key(articleId)] = scrollY.coerceAtLeast(0) }
    }

    fun observeListIndex(articleId: String): Flow<Int> = context.scrollStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[keyIndex(articleId)] ?: 0 }

    fun observeListOffset(articleId: String): Flow<Int> = context.scrollStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[keyOffset(articleId)] ?: 0 }

    suspend fun saveList(articleId: String, index: Int, offset: Int) {
        context.scrollStore.edit { prefs ->
            prefs[keyIndex(articleId)] = index.coerceAtLeast(0)
            prefs[keyOffset(articleId)] = offset.coerceAtLeast(0)
        }
    }
}
