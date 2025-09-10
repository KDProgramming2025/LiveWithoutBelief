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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.lwb.feature.reader.ReaderScreen
import info.lwb.feature.search.SearchRoute
import info.lwb.auth.RecaptchaTokenProvider
import info.lwb.auth.CachingRecaptchaProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authFacade: AuthFacade
    @Inject lateinit var recaptchaProvider: RecaptchaTokenProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialLink = intent?.dataString
        // Wrap provider with a short-lived cache (60s) to reduce repeated network calls.
        val cachedProvider = CachingRecaptchaProvider(recaptchaProvider, ttlMillis = 60_000L)
        setContent { appRoot(authFacade, cachedProvider, initialLink) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val link = intent.dataString
        val cachedProvider = CachingRecaptchaProvider(recaptchaProvider, ttlMillis = 60_000L)
        setContent { appRoot(authFacade, cachedProvider, link) }
    }
}

@Composable
private fun appRoot(authFacade: AuthFacade, recaptchaProvider: RecaptchaTokenProvider, maybeLink: String?) {
    val navController = rememberNavController()
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val vm: AuthViewModel = viewModel(factory = object: androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(authFacade, recaptchaProvider) as T
                }
            })
            val state = vm.state.collectAsState()
            val activity = LocalContext.current as android.app.Activity
            // No email link flow anymore
            Column {
                when(val s = state.value) {
                    is AuthUiState.SignedOut -> {
                        PasswordAuthSection(vm = vm)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.signIn { activity } }) { Text("Google Sign In") }
                    }
                    is AuthUiState.Loading -> Text("Loading...")
                    is AuthUiState.Error -> {
                        Text("Error: ${s.message}")
                        Spacer(Modifier.height(8.dp))
                        PasswordAuthSection(vm = vm)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.signIn { activity } }) { Text("Google Sign In") }
                    }
                    is AuthUiState.RegionBlocked -> {
                        RegionBlockedBanner(message = s.message)
                        Spacer(Modifier.height(12.dp))
                        PasswordAuthSection(vm = vm)
                    }
                    is AuthUiState.SignedIn -> {
                        Text("Hello ${s.user.displayName ?: s.user.email}")
                        Button(onClick = { vm.signOut() }) { Text("Sign Out") }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { navController.navigate(Destinations.SEARCH) }) { Text("Search") }
                        appNavHost(navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailPromptView(onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    Column {
        Text("Enter your email to receive a sign-in link")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(8.dp))
        Button(onClick = { if (email.isNotBlank()) onSubmit(email.trim()) }) { Text("Send Link") }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun RegionBlockedBanner(message: String = "Google sign-in blocked here. Use username & password instead.") {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun PasswordAuthSection(vm: AuthViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.Start) {
        Text("Username / Password Access")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 8 chars)") })
        Spacer(Modifier.height(8.dp))
        Button(onClick = { if (username.isNotBlank() && password.length >= 8) vm.passwordLogin(username.trim(), password) }) { Text("Login") }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { if (username.isNotBlank() && password.length >= 8) vm.passwordRegister(username.trim(), password) }) { Text("Register") }
    }
}

private object Destinations {
    const val READER = "reader"
    const val SEARCH = "search"
}

@Composable
private fun appNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Destinations.READER) {
        composable(Destinations.READER) { ReaderScreen() }
        composable(Destinations.SEARCH) { SearchRoute() }
    }
}
