/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.webkit.JavascriptInterface
import info.lwb.core.common.log.Logger

internal class ParagraphJsBridge(private val onParagraphLongPress: (id: String, text: String) -> Unit) {
    private val tag = "ReaderWeb"
    private val prefix = "ParaLongPress:"

    @JavascriptInterface
    fun onParagraphLongPress(id: String?, text: String?) {
        val pid = id?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        if (pid.isBlank() || body.isBlank()) {
            Logger.d(tag) { "$prefix ignored blank id/text" }
            return
        }
        Logger.d(tag) { "$prefix id=$pid len=${body.length}" }
        try {
            onParagraphLongPress(pid, body)
        } catch (_: Throwable) {
            // swallow to avoid JS callback crash
        }
    }
}
