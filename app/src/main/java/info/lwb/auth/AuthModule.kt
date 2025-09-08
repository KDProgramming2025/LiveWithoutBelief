package info.lwb.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import info.lwb.BuildConfig
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthFacade(impl: FirebaseCredentialAuthFacade): AuthFacade
    @Binds
    @Singleton
    abstract fun bindSecureStorage(impl: EncryptedPrefsSecureStorage): SecureStorage
    @Binds
    @Singleton
    abstract fun bindSessionValidator(impl: RemoteSessionValidator): SessionValidator
    @Binds
    @Singleton
    abstract fun bindTokenRefresher(impl: FirebaseTokenRefresher): TokenRefresher
    @Binds
    @Singleton
    abstract fun bindSignInExecutor(impl: FirebaseSignInExecutor): SignInExecutor
    @Binds
    @Singleton
    abstract fun bindOneTapProvider(impl: CredentialManagerOneTapProvider): OneTapCredentialProvider
    @Binds
    @Singleton
    abstract fun bindCredentialCall(impl: RealCredentialCall): CredentialCall
    @Binds
    @Singleton
    abstract fun bindRevocationStore(impl: PrefsRevocationStore): RevocationStore
    @Binds
    @Singleton
    abstract fun bindRecaptchaProvider(impl: GoogleRecaptchaTokenProvider): RecaptchaTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AuthProvisionModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideEncryptedStorage(@ApplicationContext context: Context): EncryptedPrefsSecureStorage =
        EncryptedPrefsSecureStorage(context)

    @Provides
    @Singleton
    fun provideGoogleSignInClient(): GoogleSignInClientFacade = DefaultGoogleSignInClientFacade()

    @Provides
    @Singleton
    fun provideGoogleSignInIntentExecutor(): GoogleSignInIntentExecutor = ActivityResultGoogleSignInExecutor()

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @AuthBaseUrl
    fun provideAuthBaseUrl(): String = BuildConfig.AUTH_BASE_URL

    // Provide composite including logging (could extend with metrics later)
    @Provides
    @Singleton
    fun provideCompositeValidationObserver(
        logging: LoggingValidationObserver,
        metrics: MetricsValidationObserver,
    ): ValidationObserver {
        val sampledMetrics: ValidationObserver = SamplingValidationObserver(
            upstream = metrics,
            samplePermille = BuildConfig.AUTH_VALIDATION_METRICS_SAMPLE_PERMILLE
        )
        val exporter = SnapshotExportValidationObserver(metrics)
        return NoopValidationObserver
            .and(logging)
            .and(sampledMetrics)
            .and(exporter)
    }

    @Provides
    @Singleton
    fun provideAppCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAutoTokenRefresher(
        storage: SecureStorage,
        authFacade: AuthFacade,
        appScope: CoroutineScope,
        refreshConfig: TokenRefreshConfig,
    ): AutoTokenRefresher = AutoTokenRefresher(storage, authFacade, appScope, refreshConfig).apply { start() }

    @Provides
    @Singleton
    fun assertServerClientId(): ServerClientIdGuard {
        val id = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        require(id.isNotBlank() && id != "CHANGE_ME_SERVER_CLIENT_ID") {
            "GOOGLE_SERVER_CLIENT_ID is unset or placeholder. Provide via env var or Gradle property GOOGLE_SERVER_CLIENT_ID."
        }
        return ServerClientIdGuard(id)
    }

    @Provides
    @Singleton
    fun provideTokenRefreshConfig(): TokenRefreshConfig = TokenRefreshConfig(
        refreshLeadTimeSeconds = info.lwb.BuildConfig.AUTH_REFRESH_LEAD_SECONDS,
        pollIntervalSeconds = info.lwb.BuildConfig.AUTH_REFRESH_POLL_SECONDS
    )

    @Provides
    @Singleton
    fun provideDynamicValidationRetryPolicy(): ValidationRetryPolicy = ValidationRetryPolicy(
        maxAttempts = info.lwb.BuildConfig.AUTH_VALIDATION_MAX_ATTEMPTS,
        baseDelayMs = info.lwb.BuildConfig.AUTH_VALIDATION_BASE_DELAY_MS,
        backoffMultiplier = info.lwb.BuildConfig.AUTH_VALIDATION_BACKOFF_MULT
    )

}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@JvmInline
value class ServerClientIdGuard(val value: String)

interface CredentialCall {
    suspend fun get(activity: android.app.Activity, request: androidx.credentials.GetCredentialRequest): androidx.credentials.GetCredentialResponse
}

class RealCredentialCall @javax.inject.Inject constructor() : CredentialCall {
    override suspend fun get(
        activity: android.app.Activity,
        request: androidx.credentials.GetCredentialRequest
    ): androidx.credentials.GetCredentialResponse {
        val cm = androidx.credentials.CredentialManager.create(activity)
        return cm.getCredential(activity, request)
    }
}

class CredentialManagerOneTapProvider @javax.inject.Inject constructor(
    private val call: CredentialCall
) : OneTapCredentialProvider {
    override suspend fun getIdToken(activity: android.app.Activity): String? = try {
    if (info.lwb.BuildConfig.DEBUG) android.util.Log.d("AuthFlow", "CredentialManager:getIdToken:start")
        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setServerClientId(info.lwb.BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call.get(activity, request)
        }
        val credential = response.credential
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
            if (info.lwb.BuildConfig.DEBUG) android.util.Log.d("AuthFlow", "CredentialManager:success hasToken=${googleCred.idToken.isNotEmpty()}")
            googleCred.idToken
        } else null
    } catch (e: Exception) {
        if (info.lwb.BuildConfig.DEBUG) runCatching { android.util.Log.w("AuthFlow", "CredentialManager:failure", e) }
        null
    }
}