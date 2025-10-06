package info.lwb.data.network

import info.lwb.core.common.log.Logger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Request
import java.io.IOException

/**
 * Lightweight OkHttp logging interceptor that routes output through the unified [Logger].
 *
 * Levels:
 *  - [Level.NONE]: no logging.
 *  - [Level.BASIC]: method, URL, response code, elapsed time.
 *  - [Level.HEADERS]: BASIC plus request/response headers and basic body metadata.
 */
class LwbOkHttpLoggingInterceptor(private val level: Level = Level.BASIC) : Interceptor {
    /** Logging verbosity level for HTTP events. */
    enum class Level {
        /** Disable logging entirely. */
        NONE,

        /** Log request line and response status/latency. */
        BASIC,

        /** Include headers and body size/type metadata. */
        HEADERS,
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (level == Level.NONE) {
            return chain.proceed(chain.request())
        }
        val request = chain.request()
        val startNs = System.nanoTime()
        logRequest(request)
        return try {
            val response = chain.proceed(request)
            val tookMs = (System.nanoTime() - startNs) / NANOS_PER_MILLI
            logResponse(response, tookMs)
            response
        } catch (io: IOException) {
            Logger.w(TAG, { "http failed url=${request.url} msg=${io.message}" }, io)
            throw io
        }
    }

    private fun logRequest(request: Request) {
        if (level == Level.BASIC || level == Level.HEADERS) {
            Logger.d(TAG) { "--> ${request.method} ${request.url}" }
        }
        if (level == Level.HEADERS) {
            for (i in 0 until request.headers.size) {
                val name = request.headers.name(i)
                val value = request.headers.value(i)
                Logger.v(TAG) { "hdr $name: $value" }
            }
            val body = request.body
            if (body != null) {
                Logger.v(TAG) { "body contentLength=${body.contentLength()} contentType=${body.contentType()}" }
            }
        }
    }

    private fun logResponse(response: Response, tookMs: Long) {
        if (level == Level.BASIC || level == Level.HEADERS) {
            Logger.d(TAG) { "<-- ${response.code} ${response.message} url=${response.request.url} (${tookMs}ms)" }
        }
        if (level == Level.HEADERS) {
            val headers = response.headers
            for (i in 0 until headers.size) {
                val name = headers.name(i)
                val value = headers.value(i)
                Logger.v(TAG) { "hdr $name: $value" }
            }
            val body = response.body
            if (body != null) {
                Logger.v(TAG) { "body contentType=${body.contentType()} contentLength=${body.contentLength()}" }
            }
        }
    }

    private companion object {
        /** Logger reference string for OkHttp events (mapped to global tag). */
        const val TAG = "OkHttp"

        /** Number of nanoseconds in one millisecond. */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
