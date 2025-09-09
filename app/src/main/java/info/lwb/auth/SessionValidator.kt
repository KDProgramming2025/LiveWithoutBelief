package info.lwb.auth

import javax.inject.Inject
import javax.inject.Singleton

interface SessionValidator {
    suspend fun validate(idToken: String): Boolean
    suspend fun revoke(idToken: String)
    /**
     * Rich validation result. Default bridges to legacy boolean method for backward compatibility.
     * Implementations may override to provide error taxonomy for observability & UX.
     */
    suspend fun validateDetailed(idToken: String): ValidationResult = ValidationResult(validate(idToken), null)
}

@Singleton
class NoopSessionValidator @Inject constructor(): SessionValidator {
    override suspend fun validate(idToken: String): Boolean = true // placeholder until backend ready
    override suspend fun revoke(idToken: String) { /* no-op */ }
    override suspend fun validateDetailed(idToken: String): ValidationResult = ValidationResult(true, null)
}

/** Outcome of session validation. When isValid=true, error is null. */
data class ValidationResult(val isValid: Boolean, val error: ValidationError?)

/** Classification of validation failure causes for UI decisions & logging. */
sealed interface ValidationError {
    /** Network exception / connectivity / timeout. */
    data object Network: ValidationError
    /** Explicit 401 / invalid or expired token. */
    data object Unauthorized: ValidationError
    /** 5xx server side failure. */
    data class Server(val code: Int): ValidationError
    /** Any other non-success unexpected HTTP code or parsing anomaly. */
    data class Unexpected(val message: String?): ValidationError
}