/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.tts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ReaderTtsViewModel(app: Application) : AndroidViewModel(app) {
    private val tts = TtsManager(app)

    fun ensureReady(locale: Locale = Locale.getDefault(), onReady: (Boolean) -> Unit) {
        // Ensure the callback is invoked on Main so UI/WebView ops are safe
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { tts.init(locale) }
            onReady(ok)
        }
    }

    fun speak(text: String) { viewModelScope.launch(Dispatchers.IO) { tts.speak(text) } }
    fun speakWithResult(text: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { tts.speak(text) }
            onResult(result)
        }
    }
    fun stop() { tts.stop() }

    fun setRate(rate: Float) { tts.setRate(rate) }
    fun getRate(): Float = tts.getRate()

    override fun onCleared() { super.onCleared(); tts.shutdown() }
}
