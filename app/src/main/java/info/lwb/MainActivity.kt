/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import info.lwb.auth.AuthFacade
import info.lwb.auth.AuthViewModel
import info.lwb.auth.AuthUiState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.lwb.feature.reader.ReaderScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authFacade: AuthFacade

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { appRoot(authFacade) }
    }
}

@Composable
private fun appRoot(authFacade: AuthFacade) {
    val navController = rememberNavController()
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val vm: AuthViewModel = viewModel(factory = object: androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(authFacade) as T
                }
            })
            val state = vm.state.collectAsState()
            Column {
                when(val s = state.value) {
                    is AuthUiState.SignedOut -> {
                        Button(onClick = { vm.signIn { this@MainActivity } }) { Text("One-Tap Sign In") }
                    }
                    is AuthUiState.Loading -> Text("Loading...")
                    is AuthUiState.Error -> {
                        Text("Error: ${s.message}")
                        Button(onClick = { vm.signIn { this@MainActivity } }) { Text("Retry Sign-In") }
                    }
                    is AuthUiState.SignedIn -> {
                        Text("Hello ${s.user.displayName ?: s.user.email}")
                        Button(onClick = { vm.signOut() }) { Text("Sign Out") }
                        appNavHost(navController)
                    }
                }
            }
        }
    }
}

private object Destinations {
    const val READER = "reader"
}

@Composable
private fun appNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Destinations.READER) {
        composable(Destinations.READER) { ReaderScreen() }
    }
}
