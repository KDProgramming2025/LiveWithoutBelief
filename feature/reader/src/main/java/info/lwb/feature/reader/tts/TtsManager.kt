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
import kotlin.math.min

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var rate: Float = 1.0f

    suspend fun init(locale: Locale = Locale.getDefault()): Boolean = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val applied = setBestLocale(locale)
                preferOfflineVoice(if (applied != null) applied else Locale.getDefault())
                // Apply any pre-selected speech rate
                setRate(rate)
            }
            if (!cont.isCompleted) cont.resume(ready, onCancellation = null)
        }
    }

    /**
     * Try to set the best available locale. Returns the locale actually applied, or null if none supported.
     */
    private fun setBestLocale(preferred: Locale): Locale? {
        val t = tts ?: return null
        fun apply(loc: Locale): Boolean {
            return try {
                val res = t.setLanguage(loc)
                // setLanguage returns >= 0 when supported/available; < 0 when missing/not supported
                res >= 0
            } catch (_: Throwable) { false }
        }
        val candidates = buildList {
            add(preferred)
            add(Locale(preferred.language))
            if (preferred.language != Locale.US.language) add(Locale.US)
            if (preferred.language != Locale.UK.language) add(Locale.UK)
            add(Locale.ENGLISH)
            add(Locale.getDefault())
        }
        for (loc in candidates) {
            if (apply(loc)) return loc
        }
        return null
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

    /**
     * Speak text with automatic chunking to avoid engine limits (~4000 chars). Returns 0 if queued, -1 on error.
     */
    fun speak(text: String, queue: Int = TextToSpeech.QUEUE_FLUSH): Int {
        val t = tts ?: return TextToSpeech.ERROR
        if (!ready) return TextToSpeech.ERROR
        // Ensure the current rate is applied
        try { t.setSpeechRate(rate) } catch (_: Throwable) {}
        val chunks = chunkText(text)
        var result = TextToSpeech.SUCCESS
        var first = true
        for ((i, part) in chunks.withIndex()) {
            val qMode = if (first) queue else TextToSpeech.QUEUE_ADD
            val utteranceId = "lwb-tts-" + System.currentTimeMillis().toString() + "-" + i
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                t.speak(part, qMode, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                t.speak(part, qMode, null)
            }
            if (r == TextToSpeech.ERROR) result = TextToSpeech.ERROR
            first = false
        }
        return result
    }

    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2.0f)
        val t = tts ?: return
        try { t.setSpeechRate(rate) } catch (_: Throwable) {}
    }

    fun getRate(): Float = rate

    private fun chunkText(text: String, maxLen: Int = 3900): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val out = mutableListOf<String>()
        // Split by paragraphs first
        val paragraphs = text.split("\n\n").flatMap { it.split("\r\n\r\n") }
        for (para in paragraphs) {
            var p = para.trim()
            if (p.isEmpty()) continue
            while (p.length > maxLen) {
                // try to cut at sentence boundary within window
                val window = p.substring(0, min(maxLen, p.length))
                val lastDot = window.lastIndexOf('.')
                val lastQ = window.lastIndexOf('?')
                val lastEx = window.lastIndexOf('!')
                val cutAt = listOf(lastDot, lastQ, lastEx).maxOrNull() ?: -1
                val idx = if (cutAt >= 100) cutAt + 1 else window.lastIndexOf(' ')
                val cut = if (idx in 1 until window.length) idx else window.length
                out.add(p.substring(0, cut).trim())
                p = p.substring(cut).trimStart()
            }
            if (p.isNotEmpty()) out.add(p)
        }
        // Fallback: if we somehow produced no chunks, chunk hard
        if (out.isEmpty()) {
            var i = 0
            while (i < text.length) {
                val end = min(i + maxLen, text.length)
                out.add(text.substring(i, end))
                i = end
            }
        }
        return out
    }

    fun stop() { tts?.stop() }
    fun isReady(): Boolean = ready
    fun shutdown() { tts?.shutdown(); tts = null; ready = false }

    override fun onInit(status: Int) { /* unused: we use inline lambda */ }
}
