/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.reader.ui

import android.webkit.JavascriptInterface
import info.lwb.core.common.log.Logger

internal class MediaJsBridge(
    private val onMediaQuestion: (kind: String, src: String) -> Unit,
) {
    private val tag = "ReaderWeb"
    private val prefix = "MediaQuestion:"

    @JavascriptInterface
    fun onMediaQuestion(kind: String?, src: String?) {
        val k = kind?.trim().orEmpty()
        val s = src?.trim().orEmpty()
        if (k.isBlank() || s.isBlank()) {
            Logger.d(tag) { "$prefix ignored blank kind/src" }
            return
        }
        try {
            Logger.d(tag) { "$prefix kind=$k src=${s.take(128)}" }
        } catch (_: Throwable) {
            // ignore
        }
        try {
            onMediaQuestion(k, s)
        } catch (_: Throwable) {
            // swallow
        }
    }
}
