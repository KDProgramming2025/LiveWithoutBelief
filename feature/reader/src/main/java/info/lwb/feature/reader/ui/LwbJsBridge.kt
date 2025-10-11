/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.webkit.JavascriptInterface
import info.lwb.core.common.log.Logger

/** Single WebView JS bridge exposed as window.LwbBridge with multiple callbacks. */
internal class LwbJsBridge(
    private val onParagraphLongPress: ((id: String, text: String) -> Unit)?,
    private val onMediaQuestion: ((kind: String, src: String) -> Unit)?,
) {
    private val tag = "ReaderWeb"

    @JavascriptInterface
    fun onParagraphLongPress(id: String?, text: String?) {
        val pid = id?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (pid.isBlank() || body.isBlank()) {
            Logger.d(tag) { "ParaLongPress: ignored blank id/text" }
            return
        }
        try { Logger.d(tag) { "ParaLongPress: id=$pid len=${body.length}" } } catch (_: Throwable) {}
        try { onParagraphLongPress?.invoke(pid, body) } catch (_: Throwable) {}
    }

    @JavascriptInterface
    fun onMediaQuestion(kind: String?, src: String?) {
        val k = kind?.trim().orEmpty()
        val s = src?.trim().orEmpty()
        if (k.isBlank() || s.isBlank()) {
            Logger.d(tag) { "MediaQuestion: ignored blank kind/src" }
            return
        }
        try { Logger.d(tag) { "MediaQuestion: kind=$k src=${s.take(128)}" } } catch (_: Throwable) {}
        try { onMediaQuestion?.invoke(k, s) } catch (_: Throwable) {}
    }
}
