/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val THEME_FLOW_STOP_TIMEOUT_MS = 5_000L

/**
 * ViewModel exposing the current [ThemeMode] selection and persisting user changes.
 * It converts the repository flow into a hot [StateFlow] with an active subscription
 * timeout to conserve resources when UI is not observing.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: ThemePreferenceRepository) : ViewModel() {
    /** Hot state of the selected theme mode (system / light / dark). */
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(THEME_FLOW_STOP_TIMEOUT_MS),
        initialValue = ThemeMode.SYSTEM,
    )

    /** Persist a new [ThemeMode] selection. */
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }
}
