/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialException
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

    /** Binds Credential Manager backed one‑tap provider. */
    @Binds
    @Singleton
    abstract fun bindOneTapProvider(impl: CredentialManagerOneTapProvider): OneTapCredentialProvider

    /** Binds low level credential call abstraction (seam for testing and fakes). */
    @Binds
    @Singleton
    abstract fun bindCredentialCall(impl: RealCredentialCall): CredentialCall

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

    /** Provides abstraction over Google Sign-In client implementation. */
    @Provides
    @Singleton
    fun provideGoogleSignInClient(): GoogleSignInClientFacade = DefaultGoogleSignInClientFacade()

    /** Provides an executor for launching Google Sign-In intents (Activity Result API). */
    @Provides
    @Singleton
    fun provideGoogleSignInIntentExecutor(): GoogleSignInIntentExecutor = ActivityResultGoogleSignInExecutor()

    /** Provides auth-scoped OkHttp client with a small call timeout appropriate for auth endpoints. */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttp(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(AUTH_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /** Provides base URL for auth service (switchable via build config). */
    @Provides
    @AuthBaseUrl
    fun provideAuthBaseUrl(): String = BuildConfig.AUTH_BASE_URL

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
    ): AutoTokenRefresher =
        AutoTokenRefresher(
            storage,
            authFacade,
            sessionValidator,
            appScope,
            refreshConfig,
        ).apply { start() }

    /** Ensures server client id is configured correctly and wraps it for DI (non placeholder). */
    @Provides
    @Singleton
    fun assertServerClientId(): ServerClientIdGuard {
        val id = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        require(id.isNotBlank() && id != "CHANGE_ME_SERVER_CLIENT_ID") {
            "GOOGLE_SERVER_CLIENT_ID is unset or placeholder. " +
                "Provide via env var or Gradle property GOOGLE_SERVER_CLIENT_ID."
        }
        return ServerClientIdGuard(id)
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
    fun provideTokenRefreshConfig(): TokenRefreshConfig =
        TokenRefreshConfig(
            refreshLeadTimeSeconds = info.lwb.BuildConfig.AUTH_REFRESH_LEAD_SECONDS,
            pollIntervalSeconds = info.lwb.BuildConfig.AUTH_REFRESH_POLL_SECONDS,
        )

    /** Provides retry & backoff policy for remote validation attempts. */
    @Provides
    @Singleton
    fun provideDynamicValidationRetryPolicy(): ValidationRetryPolicy =
        ValidationRetryPolicy(
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

/**
 * Value class wrapper signaling the contained server client id has been validated.
 * @property value Non-blank, non-placeholder server client id used for Google sign in flows.
 */
@JvmInline
value class ServerClientIdGuard(val value: String)

/** Abstraction over credential retrieval allowing substitution and testing. */
interface CredentialCall {
    /** Performs credential retrieval using the given Activity and request. */
    suspend fun get(
        activity: android.app.Activity,
        request: androidx.credentials.GetCredentialRequest,
    ): androidx.credentials.GetCredentialResponse
}

/** Production implementation delegating to [androidx.credentials.CredentialManager]. */
class RealCredentialCall @javax.inject.Inject constructor() : CredentialCall {
    override suspend fun get(
        activity: android.app.Activity,
        request: androidx.credentials.GetCredentialRequest,
    ): androidx.credentials.GetCredentialResponse {
        val cm = androidx.credentials.CredentialManager.create(activity)
        return cm.getCredential(activity, request)
    }
}

/** One Tap provider backed by Credential Manager returning a Google ID token when available. */
class CredentialManagerOneTapProvider @javax.inject.Inject constructor(
    private val call: CredentialCall,
) : OneTapCredentialProvider {
    override suspend fun getIdToken(activity: android.app.Activity): String? =
        try {
            if (info.lwb.BuildConfig.DEBUG) {
                android.util.Log.d(
                    LOG_TAG_AUTH,
                    LOG_MSG_START + info.lwb.BuildConfig.GOOGLE_SERVER_CLIENT_ID
                        .take(LOG_PREFIX_LEN) + "…",
                )
            }
            val googleIdOption =
                com.google.android.libraries.identity.googleid.GetGoogleIdOption
                    .Builder()
                    .setServerClientId(info.lwb.BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
            val request =
                androidx.credentials.GetCredentialRequest
                    .Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
            val response =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    call.get(activity, request)
                }
            val credential = response.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type ==
                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred =
                    com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                        .createFrom(credential.data)
                if (info.lwb.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        LOG_TAG_AUTH,
                        LOG_MSG_SUCCESS + "${googleCred.idToken.isNotEmpty()}",
                    )
                }
                googleCred.idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            if (info.lwb.BuildConfig.DEBUG) {
                runCatching {
                    android.util.Log.w(
                        LOG_TAG_AUTH,
                        LOG_MSG_FAILURE + e.message,
                        e,
                    )
                }
            }
            null
        }
}
