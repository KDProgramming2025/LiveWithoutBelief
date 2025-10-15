/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ConnectionSpec
import okhttp3.CipherSuite
import okhttp3.TlsVersion
import info.lwb.core.common.log.Logger
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing network stack singletons: JSON serializer, OkHttp client,
 * Retrofit instance, and typed service APIs.
 */
@OptIn(ExperimentalSerializationApi::class)
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Peek limit (bytes) for logging the articles list JSON response body without flooding logs.
    private const val ARTICLES_LIST_BODY_PEEK_LIMIT_BYTES: Long = 256 * 1024
    private const val ARTICLES_LOG_TAG = "ArticlesRaw"

    /**
     * Provides a configured [Json] instance for kotlinx serialization.
     *
     * Unknown keys are ignored so server side additive changes do not break older clients.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Provides a shared [OkHttpClient] with basic logging for lightweight request/response visibility.
     */
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        // Some reverse proxies on nonstandard TLS ports (e.g., 44443) can mis-handle HTTP/2 ALPN
        // which leads to TLS handshake closures on Android. Force HTTP/1.1 only to ensure
        // broad compatibility with such edge deployments.
        val http1Only = listOf(Protocol.HTTP_1_1)
        builder.protocols(http1Only)
        // Prefer TLS 1.3 with fallback to 1.2; specify common TLS 1.2 ciphers.
        val csBuilder = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        csBuilder.tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        csBuilder.cipherSuites(
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        )
        val tlsSpec = csBuilder.build()
        builder.connectionSpecs(listOf(tlsSpec))
        // Interceptor to log raw (or empty) responses for any article-related endpoints
        builder.addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                val res = chain.proceed(req)
                return try {
                    val urlStr = req.url.toString()
                    if (urlStr.contains("/articles")) {
                        val code = res.code
                        val body = res.body
                        val peek = if (body != null) {
                            res.peekBody(ARTICLES_LIST_BODY_PEEK_LIMIT_BYTES).string()
                        } else {
                            ""
                        }
                        val length = body?.contentLength() ?: -1
                        val contentType = body?.contentType()?.toString() ?: "(no-body)"
                        if (peek.isNotEmpty()) {
                            Logger.d(ARTICLES_LOG_TAG) {
                                "code=" + code +
                                    ", type=" + contentType +
                                    ", length=" + length +
                                    "\n" + peek
                            }
                        } else {
                            Logger.d(ARTICLES_LOG_TAG) {
                                "code=" + code +
                                    ", type=" + contentType +
                                    ", length=" + length +
                                    " (empty body)"
                            }
                        }
                    }
                    res
                } catch (_: Throwable) {
                    res
                }
            }
        })
        builder.addInterceptor(
            LwbOkHttpLoggingInterceptor(LwbOkHttpLoggingInterceptor.Level.BASIC),
        )
        return builder.build()
    }

    /**
     * Provides a [Retrofit] instance configured with the JSON converter and shared HTTP client.
     *
     * @param json serialization engine
     * @param client shared OkHttp client
     * @param baseUrl injected API base URL string
     */
    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient, @Named("apiBaseUrl") baseUrl: String): Retrofit {
        val builder = Retrofit.Builder()
        builder.baseUrl(baseUrl)
        builder.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        builder.client(client)
        return builder.build()
    }

    /**
     * Provides the API interface for article related endpoints.
     */
    @Provides
    @Singleton
    fun provideArticleApi(retrofit: Retrofit): ArticleApi = retrofit.create(ArticleApi::class.java)

    /**
     * Provides the API interface for menu related endpoints.
     */
    @Provides
    @Singleton
    fun provideMenuApi(retrofit: Retrofit): MenuApi = retrofit.create(MenuApi::class.java)
}
