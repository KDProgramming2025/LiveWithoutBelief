package info.lwb.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object SignedOut: AuthUiState
    object Loading: AuthUiState
    data class SignedIn(val user: AuthUser): AuthUiState
    data class Error(val message: String): AuthUiState
}

class AuthViewModel(private val facade: AuthFacade): ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(facade.currentUser()?.let { AuthUiState.SignedIn(it) } ?: AuthUiState.SignedOut)
    val state: StateFlow<AuthUiState> = _state

    fun signIn(activityProvider: () -> android.app.Activity) {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            facade.oneTapSignIn(activityProvider()).onSuccess { user ->
                _state.value = AuthUiState.SignedIn(user)
            }.onFailure { e ->
                _state.value = AuthUiState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            runCatching { facade.signOut() }
                .onSuccess { _state.value = AuthUiState.SignedOut }
                .onFailure { e -> _state.value = AuthUiState.Error(e.message ?: "Sign-out failed") }
        }
    }
}