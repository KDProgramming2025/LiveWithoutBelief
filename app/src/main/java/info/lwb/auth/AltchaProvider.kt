/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface AltchaTokenProvider {
    suspend fun solve(activity: Activity): String?
}

@Singleton
class WebViewAltchaProvider @Inject constructor(
    @AuthBaseUrl private val baseUrl: String,
) : AltchaTokenProvider {
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun solve(activity: Activity): String? = suspendCancellableCoroutine { cont ->
        val wv = WebView(activity)
        val url = "file:///android_asset/altcha-solver.html?base=" + baseUrl
        var resumed = false
        val done: (String?) -> Unit = {
            if (!resumed) {
                resumed = true
                cont.resume(it)
            }
        }
        wv.settings.javaScriptEnabled = true
        wv.addJavascriptInterface(object {
            @JavascriptInterface fun postSolution(payload: String) { done(payload) }
        }, "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // no-op
            }
        }
        wv.loadUrl(url)
        cont.invokeOnCancellation { wv.destroy() }
    }
}
