package info.lwb.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val DS_NAME = "app_settings"
private val Context.dataStore by preferencesDataStore(name = DS_NAME)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys { val THEME = intPreferencesKey("theme_mode") }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            when (prefs[Keys.THEME] ?: 0) {
                1 -> ThemeMode.LIGHT
                2 -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = when (mode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            }
        }
    }
}
