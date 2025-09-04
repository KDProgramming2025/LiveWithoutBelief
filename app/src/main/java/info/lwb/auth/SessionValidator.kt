package info.lwb.auth

import javax.inject.Inject
import javax.inject.Singleton

interface SessionValidator {
    suspend fun validate(idToken: String): Boolean
    suspend fun revoke(idToken: String)
}

@Singleton
class NoopSessionValidator @Inject constructor(): SessionValidator {
    override suspend fun validate(idToken: String): Boolean = true // placeholder until backend ready
    override suspend fun revoke(idToken: String) { /* no-op */ }
}