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

}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient