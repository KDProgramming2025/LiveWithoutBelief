/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for validating and revoking authenticated user ID tokens.
 *
 * Implementations may call a backend endpoint to perform cryptographic verification / revocation.
 * A minimal default [validate] boolean method is provided for lightweight callers; richer semantics
 * are available via [validateDetailed] which returns a [ValidationResult] including structured
 * error information to guide user messaging or retry policies.
 */
interface SessionValidator {
    /**
     * Validate the supplied ID token.
     * @param idToken Opaque ID token string provided by the authentication provider.
     * @return true if the token is currently valid, false otherwise (reason not distinguished).
     */
    suspend fun validate(idToken: String): Boolean

    /**
     * Revoke (invalidate) the supplied ID token so it can no longer be used.
     * Implementations should swallow / map benign errors; caller treats as fire-and-forget.
     * @param idToken token being revoked.
     */
    suspend fun revoke(idToken: String)

    /**
     * Rich validation result including optional [ValidationError] taxonomy for observability & UX.
     * Default implementation bridges to legacy boolean method for backward compatibility.
     * @param idToken token to validate.
     * @return [ValidationResult] containing validity and optional error classification.
     */
    suspend fun validateDetailed(idToken: String): ValidationResult = ValidationResult(validate(idToken), null)
}

/**
 * Placeholder implementation that accepts all tokens until a real backend implementation is wired.
 */
@Singleton
class NoopSessionValidator @Inject constructor() : SessionValidator {
    /** Always returns true (development fallback). */
    override suspend fun validate(idToken: String): Boolean = true

    /** No-op revoke. */
    override suspend fun revoke(idToken: String) { /* no-op */ }

    /** Always returns a successful validation result (development fallback). */
    override suspend fun validateDetailed(idToken: String): ValidationResult = ValidationResult(true, null)
}

/**
 * Outcome of session validation.
 * @property isValid true when the token is considered valid.
 * @property error classification explaining the failure (null when [isValid] is true).
 */
data class ValidationResult(val isValid: Boolean, val error: ValidationError?)

/**
 * Classification of validation failure causes for UI decisions & logging.
 */
sealed interface ValidationError {
    /** Network exception / connectivity / timeout. */
    data object Network : ValidationError

    /** Explicit 401 / invalid or expired token. */
    data object Unauthorized : ValidationError

    /** 5xx server side failure. @property code HTTP status code. */
    data class Server(val code: Int) : ValidationError

    /** Any other non-success unexpected HTTP code or parsing anomaly. @property message optional detail. */
    data class Unexpected(val message: String?) : ValidationError
}
