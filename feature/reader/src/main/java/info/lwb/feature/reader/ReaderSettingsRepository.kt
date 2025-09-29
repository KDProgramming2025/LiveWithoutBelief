/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.reader

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val DS_NAME = "reader_settings"

private val Context.dataStore by preferencesDataStore(name = DS_NAME)

internal class ReaderSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    enum class ReaderBackground(val key: String) {
        Sepia("sepia"),
        Paper("paper"),
        Gray("gray"),
        Slate("slate"),
        Charcoal("charcoal"),
        Olive("olive"),
        Night("night"),
    }

    private object Keys {
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val LINE_HEIGHT = doublePreferencesKey("line_height")
        val BACKGROUND = stringPreferencesKey("background_theme")
    }

    val fontScale: Flow<Double> =
        context.dataStore.data.catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }.map { prefs -> prefs[Keys.FONT_SCALE] ?: 1.0 }

    val lineHeight: Flow<Double> =
        context.dataStore.data.catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }.map { prefs -> prefs[Keys.LINE_HEIGHT] ?: 1.2 }

    val background: Flow<ReaderBackground> =
        context.dataStore.data.catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }.map { prefs ->
            when (prefs[Keys.BACKGROUND]) {
                ReaderBackground.Sepia.key -> {
                    ReaderBackground.Sepia
                }
                ReaderBackground.Paper.key -> {
                    ReaderBackground.Paper
                }
                ReaderBackground.Gray.key -> {
                    ReaderBackground.Gray
                }
                ReaderBackground.Slate.key -> {
                    ReaderBackground.Slate
                }
                ReaderBackground.Charcoal.key -> {
                    ReaderBackground.Charcoal
                }
                ReaderBackground.Olive.key -> {
                    ReaderBackground.Olive
                }
                ReaderBackground.Night.key -> {
                    ReaderBackground.Night
                }
                else -> {
                    // Legacy or unset (including old 'system'): default to Paper
                    ReaderBackground.Paper
                }
            }
        }

    suspend fun setFontScale(v: Double) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = v.coerceIn(0.8, 1.6) }
    }

    suspend fun setLineHeight(v: Double) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = v.coerceIn(1.0, 2.0) }
    }

    suspend fun setBackground(bg: ReaderBackground) {
        context.dataStore.edit { it[Keys.BACKGROUND] = bg.key }
    }
}
