/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val DS_NAME = "app_settings"
private val Context.dataStore by preferencesDataStore(name = DS_NAME)

/**
 * App theme appearance modes exposed to the UI layer.
 *
 * SYSTEM: Follow the device's current day/night setting.
 * LIGHT: Force light theme.
 * DARK: Force dark theme.
 */
enum class ThemeMode {
    /** Follow the device's current system (day/night) setting. */
    SYSTEM,

    /** Force light theme regardless of system setting. */
    LIGHT,

    /** Force dark theme regardless of system setting. */
    DARK,
}

/**
 * Repository responsible for persisting and exposing user theme preference using DataStore.
 * Wraps the underlying integer preference with a typed [ThemeMode] stream and mutation API.
 */
class ThemePreferenceRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val THEME = intPreferencesKey("theme_mode")
    }

    private companion object {
        // Persisted integer encodings for ThemeMode (backwards-compatible if we add modes later).
        private const val CODE_SYSTEM = 0
        private const val CODE_LIGHT = 1
        private const val CODE_DARK = 2
    }

    /**
     * Flow of the current [ThemeMode]. Emits updated values whenever the persisted preference changes.
     */
    val themeMode: Flow<ThemeMode> = context
        .dataStore
        .data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }.map { prefs ->
            when (prefs[Keys.THEME] ?: CODE_SYSTEM) {
                CODE_LIGHT -> {
                    ThemeMode.LIGHT
                }
                CODE_DARK -> {
                    ThemeMode.DARK
                }
                else -> {
                    ThemeMode.SYSTEM
                }
            }
        }

    /** Persist a new [ThemeMode] selection. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = when (mode) {
                ThemeMode.SYSTEM -> {
                    CODE_SYSTEM
                }
                ThemeMode.LIGHT -> {
                    CODE_LIGHT
                }
                ThemeMode.DARK -> {
                    CODE_DARK
                }
            }
        }
    }
}
