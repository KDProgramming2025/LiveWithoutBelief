/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.lwb.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ConnectionSpec
import okhttp3.CipherSuite
import okhttp3.TlsVersion
import info.lwb.data.network.LwbOkHttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module binding public authentication interfaces to their concrete implementations.
 *
 * This module only contains @Binds methods to keep construction logic isolated in provisioning
 * modules which reduces function counts per object and improves incremental builds. Each bind
 * method is documented to satisfy Detekt's documentation rules and ease discoverability.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    /** Maps the Firebase‑backed facade implementation to the high level [AuthFacade] API. */
    @Binds
    @Singleton
    abstract fun bindAuthFacade(impl: FirebaseCredentialAuthFacade): AuthFacade

    /** Binds encrypted SharedPreferences based implementation as [SecureStorage]. */
    @Binds
    @Singleton
    abstract fun bindSecureStorage(impl: EncryptedPrefsSecureStorage): SecureStorage

    /** Binds remote session validator implementation. */
    @Binds
    @Singleton
    abstract fun bindSessionValidator(impl: RemoteSessionValidator): SessionValidator

    /** Binds Firebase token refresher implementation. */
    @Binds
    @Singleton
    abstract fun bindTokenRefresher(impl: FirebaseTokenRefresher): TokenRefresher

    /** Binds Firebase sign in executor implementation. */
    @Binds
    @Singleton
    abstract fun bindSignInExecutor(impl: FirebaseSignInExecutor): SignInExecutor

    /** Binds revocation store implementation. */
    @Binds
    @Singleton
    abstract fun bindRevocationStore(impl: PrefsRevocationStore): RevocationStore

    /** Binds remote registration API implementation. */
    @Binds
    @Singleton
    abstract fun bindRegistrationApi(impl: RemoteRegistrationApi): RegistrationApi

    /** Binds remote password authentication API implementation. */
    @Binds
    @Singleton
    abstract fun bindPasswordAuthApi(impl: RemotePasswordAuthApi): PasswordAuthApi

    /** Binds WebView based Altcha token provider implementation. */
    @Binds
    @Singleton
    abstract fun bindAltchaProvider(impl: WebViewAltchaProvider): AltchaTokenProvider
}

private const val AUTH_OKHTTP_TIMEOUT_SECONDS = 10L
private const val LOG_TAG_AUTH = "AuthFlow"
private const val LOG_PREFIX_LEN = 16
private const val LOG_MSG_START = "CredentialManager:getIdToken:start serverClientId="
private const val LOG_MSG_SUCCESS = "CredentialManager:success hasToken="
private const val LOG_MSG_FAILURE = "CredentialManager:failure "

/**
 * Provides core runtime authentication dependencies (Firebase, storage, networking, coroutine scope).
 *
 * Validation / retry related providers are moved to [AuthValidationModule] to keep the function
 * count within the configured Detekt limit for an object (11).
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthProvisionModule {
    /** Provides the singleton FirebaseAuth instance. */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Provides encrypted secure storage backed by EncryptedSharedPreferences wrapper. */
    @Provides
    @Singleton
    fun provideEncryptedStorage(@ApplicationContext context: Context): EncryptedPrefsSecureStorage =
        EncryptedPrefsSecureStorage(context)

    /** Provides Identity API credential facade implementation. */
    @Provides
    @Singleton
    fun provideIdentityCredentialFacade(
        @ServerClientId serverClientId: String,
    ): IdentityCredentialFacade = DefaultIdentityCredentialFacade(serverClientId)

    /** Provides auth-scoped OkHttp client with a small call timeout appropriate for auth endpoints. */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        // Align protocol policy with main client: some TLS setups on port 44443 mis-handle HTTP/2
        // causing handshake closures. Force HTTP/1.1 for maximum compatibility.
        builder.protocols(listOf(Protocol.HTTP_1_1))
        // Match main client TLS policy: prefer TLS 1.3 with fallback to 1.2 and common ciphers
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
        // Add lightweight BASIC logging for auth HTTP calls to aid diagnosis
        builder.addInterceptor(LwbOkHttpLoggingInterceptor(LwbOkHttpLoggingInterceptor.Level.BASIC))
        builder.callTimeout(AUTH_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return builder.build()
    }

    /** Provides base URL for auth service (switchable via build config). */
    @Provides
    @AuthBaseUrl
    fun provideAuthBaseUrl(): String = requireNotNull(BuildConfig.AUTH_BASE_URL) {
        "AUTH_BASE_URL is not configured. Set APP_SERVER_HOST (and optionally APP_SERVER_SCHEME) " +
            "or provide AUTH_BASE_URL via environment/Gradle property."
    }

    /** Provides a background application-wide coroutine scope (Supervisor + Default dispatcher). */
    @Provides
    @Singleton
    fun provideAppCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Provides and eagerly starts the automatic token refresher orchestrator. */
    @Provides
    @Singleton
    fun provideAutoTokenRefresher(
        storage: SecureStorage,
        authFacade: AuthFacade,
        sessionValidator: SessionValidator,
        appScope: CoroutineScope,
        refreshConfig: TokenRefreshConfig,
    ): AutoTokenRefresher = AutoTokenRefresher(
        storage,
        authFacade,
        sessionValidator,
        appScope,
        refreshConfig,
    ).apply { start() }

    /** Ensures server client id is configured correctly and exposes it as a qualified String. */
    @Provides
    @Singleton
    @ServerClientId
    fun provideServerClientId(): String {
        val id = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        require(id.isNotBlank() && id != "CHANGE_ME_SERVER_CLIENT_ID") {
            "GOOGLE_SERVER_CLIENT_ID is unset or placeholder. Provide via env var or " +
                "Gradle property GOOGLE_SERVER_CLIENT_ID."
        }
        return id
    }
}

/**
 * Provides validation-related observers, configuration and retry policies for auth validation.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthValidationModule {
    /** Builds a composite validation observer combining logging, sampled metrics and snapshot export. */
    @Provides
    @Singleton
    fun provideCompositeValidationObserver(
        logging: LoggingValidationObserver,
        metrics: MetricsValidationObserver,
    ): ValidationObserver {
        val sampledMetrics: ValidationObserver =
            SamplingValidationObserver(
                upstream = metrics,
                samplePermille = BuildConfig.AUTH_VALIDATION_METRICS_SAMPLE_PERMILLE,
            )
        val exporter = SnapshotExportValidationObserver(metrics)
        return NoopValidationObserver
            .and(logging)
            .and(sampledMetrics)
            .and(exporter)
    }

    /** Provides configuration controlling token refresh lead time and polling. */
    @Provides
    @Singleton
    fun provideTokenRefreshConfig(): TokenRefreshConfig = TokenRefreshConfig(
        refreshLeadTimeSeconds = info.lwb.BuildConfig.AUTH_REFRESH_LEAD_SECONDS,
        pollIntervalSeconds = info.lwb.BuildConfig.AUTH_REFRESH_POLL_SECONDS,
    )

    /** Provides retry & backoff policy for remote validation attempts. */
    @Provides
    @Singleton
    fun provideDynamicValidationRetryPolicy(): ValidationRetryPolicy = ValidationRetryPolicy(
        maxAttempts = info.lwb.BuildConfig.AUTH_VALIDATION_MAX_ATTEMPTS,
        baseDelayMs = info.lwb.BuildConfig.AUTH_VALIDATION_BASE_DELAY_MS,
        backoffMultiplier = info.lwb.BuildConfig.AUTH_VALIDATION_BACKOFF_MULT,
    )
}

/** Qualifier for injecting the authentication service base URL. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthBaseUrl

/** Qualifier for injecting the dedicated authentication OkHttp client. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

/** Qualifier for validated Google server client id String. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerClientId
