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
import info.lwb.core.common.log.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val DS_NAME = "reader_settings"

private val Context.dataStore by preferencesDataStore(name = DS_NAME)

internal const val DEFAULT_FONT_SCALE = 1.0
internal const val DEFAULT_LINE_HEIGHT = 1.2
private const val MIN_FONT_SCALE = 0.8
private const val MAX_FONT_SCALE = 1.6
private const val MIN_LINE_HEIGHT = 1.0
private const val MAX_LINE_HEIGHT = 2.0

/**
 * Repository exposing reactive reader appearance settings backed by DataStore.
 * Provides font scale, line height and background theme with sane default values.
 */
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
        val FONT_SCALE = doublePreferencesKey(name = "font_scale")
        val LINE_HEIGHT = doublePreferencesKey(name = "line_height")
        val BACKGROUND = stringPreferencesKey(name = "background_theme")
    }

    private val backgroundByKey: Map<String, ReaderBackground> = ReaderBackground
        .values()
        .associateBy { it.key }

    // Shared base flow avoids Detekt chain continuation oscillation across multiple properties.
    private val basePrefsFlow: Flow<androidx.datastore.preferences.core.Preferences> = context.dataStore
        .data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    private companion object {
        const val TAG = "ReaderSettings"
    }

    val fontScale: Flow<Double> = basePrefsFlow.map { prefs ->
        val v = prefs[Keys.FONT_SCALE] ?: DEFAULT_FONT_SCALE
        Logger.d(TAG) { "load fontScale=$v" }
        v
    }

    val lineHeight: Flow<Double> = basePrefsFlow.map { prefs ->
        val v = prefs[Keys.LINE_HEIGHT] ?: DEFAULT_LINE_HEIGHT
        Logger.d(TAG) { "load lineHeight=$v" }
        v
    }

    val background: Flow<ReaderBackground> = basePrefsFlow.map { prefs ->
        val key = prefs[Keys.BACKGROUND]
        val bg = backgroundByKey[key] ?: ReaderBackground.Paper
        Logger.d(TAG) { "load background=${bg.key}" }
        bg
    }

    /** Persist a new font scale (coerced to allowed range). */
    suspend fun setFontScale(v: Double) {
        val coerced = v.coerceIn(minimumValue = MIN_FONT_SCALE, maximumValue = MAX_FONT_SCALE)
        Logger.d(TAG) { "save fontScale=$coerced" }
        context.dataStore.edit { prefs -> prefs[Keys.FONT_SCALE] = coerced }
    }

    /** Persist a new line height (coerced to allowed range). */
    suspend fun setLineHeight(v: Double) {
        val coerced = v.coerceIn(minimumValue = MIN_LINE_HEIGHT, maximumValue = MAX_LINE_HEIGHT)
        Logger.d(TAG) { "save lineHeight=$coerced" }
        context.dataStore.edit { prefs -> prefs[Keys.LINE_HEIGHT] = coerced }
    }

    suspend fun setBackground(bg: ReaderBackground) {
        Logger.d(TAG) { "save background=${bg.key}" }
        context.dataStore.edit { prefs -> prefs[Keys.BACKGROUND] = bg.key }
    }
}
