/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object SignedOut : AuthUiState
    object Loading : AuthUiState
    data class SignedIn(val user: AuthUser) : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class RegionBlocked(val message: String) : AuthUiState
}

class AuthViewModel(
    private val facade: AuthFacade,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(
        facade.currentUser()?.let { AuthUiState.SignedIn(it) } ?: AuthUiState.SignedOut,
    )
    val state: StateFlow<AuthUiState> = _state
    private var regionBlocked: Boolean = false // may repurpose for disabling Google path

    fun signIn(activityProvider: () -> android.app.Activity) {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch(mainDispatcher) {
            facade.oneTapSignIn(activityProvider()).onSuccess { user ->
                _state.value = AuthUiState.SignedIn(user)
            }.onFailure { e ->
                if (e is RegionBlockedAuthException) {
                    regionBlocked = true
                    _state.value = AuthUiState.RegionBlocked(
                        e.message ?: "Google sign-in blocked. Use username & password.",
                    )
                } else {
                    _state.value = AuthUiState.Error(e.message ?: "Sign-in failed")
                }
            }
        }
    }

    // Password flows removed.

    fun signOut() {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch(mainDispatcher) {
            runCatching { facade.signOut() }
                .onSuccess { _state.value = AuthUiState.SignedOut }
                .onFailure { e -> _state.value = AuthUiState.Error(e.message ?: "Sign-out failed") }
        }
    }
}
