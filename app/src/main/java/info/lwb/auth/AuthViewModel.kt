/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Auth UI finite-state representation consumed by the UI layer. */
sealed interface AuthUiState {
    /** No user authenticated. */
    object SignedOut : AuthUiState

    /** An auth operation is running. */
    object Loading : AuthUiState

    /** Authenticated state. @property user signed-in user model. */
    data class SignedIn(val user: AuthUser) : AuthUiState

    /** Error state. @property message user-facing message. */
    data class Error(val message: String) : AuthUiState

    /** Region block state. @property message guidance. */
    data class RegionBlocked(val message: String) : AuthUiState
}

/**
 * Authentication ViewModel orchestrating sign-in/out and password flows exposing a single immutable [state] stream.
 *
 * Responsibilities:
 *  - Delegates auth operations to [AuthFacade].
 *  - Applies simple state machine transitions to drive UI.
 *  - Performs optional ALTCHA challenge solving before password registration.
 *
 * Threading: public methods launch coroutines on [Dispatchers.Main].
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authFacade: AuthFacade,
    private val altchaProvider: AltchaTokenProvider?,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(
        authFacade.currentUser()?.let { AuthUiState.SignedIn(it) } ?: AuthUiState.SignedOut,
    )

    /** Observable immutable authentication state for UI consumption. */
    val state: StateFlow<AuthUiState> = _state

    private var regionBlockedFlag: Boolean = false

    /** Initiates Google One Tap sign-in if not already loading. */
    fun signIn(activityProvider: () -> android.app.Activity) {
        if (_state.value is AuthUiState.Loading) {
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.Main) {
            authFacade
                .oneTapSignIn(activityProvider())
                .onSuccess { user -> _state.value = AuthUiState.SignedIn(user) }
                .onFailure { e ->
                    if (e is RegionBlockedAuthException) {
                        regionBlockedFlag = true
                        _state.value = AuthUiState.RegionBlocked(
                            e.message ?: "Google sign-in blocked. Use username & password.",
                        )
                    } else {
                        _state.value = AuthUiState.Error(e.message ?: "Sign-in failed")
                    }
                }
        }
    }

    /** Performs username/password registration after attempting an ALTCHA challenge. */
    fun passwordRegister(activityProvider: () -> android.app.Activity, username: String, password: String) {
        if (_state.value is AuthUiState.Loading) {
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.Main) {
            val token = runCatching { altchaProvider?.solve(activityProvider()) }.getOrNull()
            if (token.isNullOrEmpty()) {
                _state.value = AuthUiState.Error("ALTCHA failed; please try again")
            } else {
                authFacade
                    .register(username, password, token)
                    .onSuccess { _state.value = AuthUiState.SignedIn(it) }
                    .onFailure { _state.value = AuthUiState.Error(it.message ?: "Register failed") }
            }
        }
    }

    /** Performs username/password login. */
    fun passwordLogin(username: String, password: String) {
        if (_state.value is AuthUiState.Loading) {
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.Main) {
            authFacade
                .passwordLogin(username, password)
                .onSuccess { _state.value = AuthUiState.SignedIn(it) }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Login failed") }
        }
    }

    /** Signs current user out (if any). */
    fun signOut() {
        if (_state.value is AuthUiState.Loading) {
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.Main) {
            runCatching { authFacade.signOut() }
                .onSuccess { _state.value = AuthUiState.SignedOut }
                .onFailure { e -> _state.value = AuthUiState.Error(e.message ?: "Sign-out failed") }
        }
    }
}
