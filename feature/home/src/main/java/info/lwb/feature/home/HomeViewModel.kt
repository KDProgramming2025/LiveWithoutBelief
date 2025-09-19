/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.GetMenuUseCase
import info.lwb.core.domain.RefreshMenuUseCase
import info.lwb.core.model.MenuItem
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMenu: GetMenuUseCase,
    private val refreshMenu: RefreshMenuUseCase,
    @Named("apiBaseUrl") val apiBaseUrl: String,
    @Named("uploadsBaseUrl") val uploadsBaseUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    fun load() {
        viewModelScope.launch {
            getMenu().collectLatest { res ->
                when (res) {
                    is Result.Loading -> _state.value = HomeUiState.Loading
                    is Result.Success -> _state.value = HomeUiState.Success(res.data)
                    is Result.Error -> _state.value = HomeUiState.Error(res.throwable.message ?: "Unknown error")
                }
            }
        }
        viewModelScope.launch { runCatching { refreshMenu() } }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val items: List<MenuItem>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
