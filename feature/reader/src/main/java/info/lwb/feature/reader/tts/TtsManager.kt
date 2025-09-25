/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false

    suspend fun init(locale: Locale = Locale.getDefault()): Boolean = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                setLocale(locale)
                preferOfflineVoice(locale)
            }
            if (!cont.isCompleted) cont.resume(ready, onCancellation = null)
        }
    }

    private fun setLocale(locale: Locale) {
        val t = tts ?: return
        try { t.language = locale } catch (_: Throwable) {}
    }

    private fun preferOfflineVoice(locale: Locale) {
        val t = tts ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voices = t.voices ?: return
                // Prefer a local/offline voice matching the language
                val candidate = voices
                    .filter { it.locale.language == locale.language }
                    .sortedWith(compareByDescending<Voice> { it.isNetworkConnectionRequired.not() }
                        .thenBy { it.quality })
                    .firstOrNull()
                if (candidate != null) t.voice = candidate
            }
        } catch (_: Throwable) {}
    }

    fun speak(text: String, queue: Int = TextToSpeech.QUEUE_FLUSH): Int {
        val t = tts ?: return TextToSpeech.ERROR
        if (!ready) return TextToSpeech.ERROR
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            t.speak(text, queue, null, "lwb-tts")
        } else {
            @Suppress("DEPRECATION")
            t.speak(text, queue, null)
        }
    }

    fun stop() { tts?.stop() }
    fun isReady(): Boolean = ready
    fun shutdown() { tts?.shutdown(); tts = null; ready = false }

    override fun onInit(status: Int) { /* unused: we use inline lambda */ }
}
