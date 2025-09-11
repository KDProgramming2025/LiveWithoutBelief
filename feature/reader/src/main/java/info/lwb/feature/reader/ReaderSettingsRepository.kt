package info.lwb.feature.reader

import android.content.Context
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DS_NAME = "reader_settings"

private val Context.dataStore by preferencesDataStore(name = DS_NAME)

class ReaderSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val LINE_HEIGHT = doublePreferencesKey("line_height")
    }

    val fontScale: Flow<Double> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.FONT_SCALE] ?: 1.0 }

    val lineHeight: Flow<Double> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.LINE_HEIGHT] ?: 1.2 }

    suspend fun setFontScale(v: Double) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = v.coerceIn(0.8, 1.6) }
    }

    suspend fun setLineHeight(v: Double) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = v.coerceIn(1.0, 2.0) }
    }
}