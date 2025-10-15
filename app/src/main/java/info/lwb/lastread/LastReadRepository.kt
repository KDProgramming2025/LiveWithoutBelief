/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.lastread

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DS_NAME = "last_read"

private val Context.lastReadStore by preferencesDataStore(name = DS_NAME)

/**
 * Snapshot of the last opened article.
 *
 * @property articleId stable id used for navigation to ReaderByIdRoute
 * @property indexUrl fully qualified URL for the article's web index (used by WebView)
 */
data class LastRead(val articleId: String, val indexUrl: String)

/**
 * Persist and expose the last opened article so Home can offer "Continue reading".
 * Minimal and app-scoped to avoid cross-feature coupling.
 */
@Singleton
class LastReadRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val ARTICLE_ID = stringPreferencesKey("article_id")
        val INDEX_URL = stringPreferencesKey("index_url")
    }

    /** Reactive stream of the last-read article; null when none recorded. */
    val flow: Flow<LastRead?> = context.lastReadStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val id = prefs[Keys.ARTICLE_ID]
            val url = prefs[Keys.INDEX_URL]
            if (!id.isNullOrBlank() && !url.isNullOrBlank()) {
                LastRead(id, url)
            } else {
                null
            }
        }

    /** Persist the last opened [articleId] and [indexUrl]. No-op for blank values. */
    suspend fun save(articleId: String, indexUrl: String) {
        if (articleId.isBlank() || indexUrl.isBlank()) {
            return
        }
        context.lastReadStore.edit { prefs ->
            prefs[Keys.ARTICLE_ID] = articleId
            prefs[Keys.INDEX_URL] = indexUrl
        }
    }
}
