/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.scrollStore by preferencesDataStore(name = "reader_scroll")

internal class ScrollRepository(private val context: Context) {
    private fun key(articleId: String) = intPreferencesKey("scroll_$articleId")
    private fun keyAnchor(articleId: String) = stringPreferencesKey("anchor_$articleId")
    private fun keyIndex(articleId: String) = intPreferencesKey("list_index_$articleId")
    private fun keyOffset(articleId: String) = intPreferencesKey("list_offset_$articleId")

    fun observe(articleId: String): Flow<Int> =
        context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                prefs[key(articleId)] ?: 0
            }

    suspend fun save(articleId: String, scrollY: Int) {
        context.scrollStore.edit { prefs -> prefs[key(articleId)] = scrollY.coerceAtLeast(0) }
    }

    fun observeAnchor(articleId: String): Flow<String> =
        context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                prefs[keyAnchor(articleId)] ?: ""
            }

    suspend fun saveAnchor(articleId: String, anchor: String?) {
        context.scrollStore.edit { prefs ->
            if (anchor.isNullOrBlank()) prefs.remove(keyAnchor(articleId)) else prefs[keyAnchor(articleId)] = anchor
        }
    }

    fun observeListIndex(articleId: String): Flow<Int> =
        context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                prefs[keyIndex(articleId)] ?: 0
            }

    fun observeListOffset(articleId: String): Flow<Int> =
        context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { prefs ->
                prefs[keyOffset(articleId)] ?: 0
            }

    suspend fun saveList(articleId: String, index: Int, offset: Int) {
        context.scrollStore.edit { prefs ->
            prefs[keyIndex(articleId)] = index.coerceAtLeast(0)
            prefs[keyOffset(articleId)] = offset.coerceAtLeast(0)
        }
    }
}
