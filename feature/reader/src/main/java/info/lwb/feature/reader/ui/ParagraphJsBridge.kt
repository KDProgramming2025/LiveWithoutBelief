/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.webkit.JavascriptInterface
import info.lwb.core.common.log.Logger

internal class ParagraphJsBridge(private val onParagraphLongPress: (id: String, text: String) -> Unit) {
    private val tag = "ReaderWeb"
    private val prefix = "ParaLongPress:"

    @Volatile
    private var lastId: String = ""

    @Volatile
    private var lastTs: Long = 0L

    private val throttleMs = PARAGRAPH_THROTTLE_MS
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun onParagraphLongPress(id: String?, text: String?) {
        val pid = id?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (pid.isBlank() || body.isBlank()) {
            Logger.d(tag) { "$prefix ignored blank id/text" }
            return
        }
        val now = System.currentTimeMillis()
        if (pid == lastId && (now - lastTs) < throttleMs) {
            // Suppress rapid duplicates from WebView
            return
        }
        lastId = pid
        lastTs = now
        Logger.d(tag) { "$prefix id=$pid len=${body.length}" }
        mainHandler.post {
            try {
                onParagraphLongPress(pid, body)
            } catch (_: Throwable) {
                // swallow to avoid JS callback crash
            }
        }
    }
}

private const val PARAGRAPH_THROTTLE_MS = 400L
