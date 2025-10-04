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

/**
 * Persistence helper for per-article scroll and list position state using DataStore.
 *
 * Stores: vertical scroll Y, anchor id, list index and list offset. All values are
 * namespaced by article id. IOExceptions during read are converted to empty prefs.
 */
internal class ScrollRepository(private val context: Context) {
    private fun key(articleId: String) = intPreferencesKey(name = "scroll_$articleId")

    private fun keyAnchor(articleId: String) = stringPreferencesKey(name = "anchor_$articleId")

    private fun keyIndex(articleId: String) = intPreferencesKey(name = "list_index_$articleId")

    private fun keyOffset(articleId: String) = intPreferencesKey(name = "list_offset_$articleId")

    /** Observe scroll Y for an article. */
    fun observe(articleId: String): Flow<Int> {
        val flow = context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
        return flow.map { prefs -> prefs[key(articleId)] ?: 0 }
    }

    /** Persist scroll Y (clamped to >= 0). */
    suspend fun save(articleId: String, scrollY: Int) {
        context.scrollStore.edit { prefs ->
            prefs[key(articleId = articleId)] = scrollY.coerceAtLeast(0)
        }
    }

    /** Observe anchor id for an article. */
    fun observeAnchor(articleId: String): Flow<String> {
        val flow = context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
        return flow.map { prefs -> prefs[keyAnchor(articleId)] ?: "" }
    }

    /** Save or clear anchor id (removed if blank). */
    suspend fun saveAnchor(articleId: String, anchor: String?) {
        context.scrollStore.edit { prefs ->
            if (anchor.isNullOrBlank()) {
                prefs.remove(keyAnchor(articleId = articleId))
            } else {
                prefs[keyAnchor(articleId = articleId)] = anchor
            }
        }
    }

    /** Observe list index for an article. */
    fun observeListIndex(articleId: String): Flow<Int> {
        val flow = context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
        return flow.map { prefs -> prefs[keyIndex(articleId)] ?: 0 }
    }

    /** Observe list pixel offset for an article. */
    fun observeListOffset(articleId: String): Flow<Int> {
        val flow = context.scrollStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
        return flow.map { prefs -> prefs[keyOffset(articleId)] ?: 0 }
    }

    /** Persist list index and offset (coerced to >= 0). */
    suspend fun saveList(articleId: String, index: Int, offset: Int) {
        context.scrollStore.edit { prefs ->
            prefs[keyIndex(articleId = articleId)] = index.coerceAtLeast(0)
            prefs[keyOffset(articleId = articleId)] = offset.coerceAtLeast(0)
        }
    }
}
