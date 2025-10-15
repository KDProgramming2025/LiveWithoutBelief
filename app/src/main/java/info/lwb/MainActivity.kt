/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.lwb.auth.AuthViewModel
import info.lwb.feature.articles.ArticlesListRoute
import info.lwb.feature.home.HomeRoute
import info.lwb.feature.reader.ReaderByIdRoute
import info.lwb.feature.search.SearchRoute
import info.lwb.feature.settings.SettingsRoute
import info.lwb.feature.settings.SettingsViewModel
import info.lwb.feature.settings.ThemeMode
import info.lwb.ui.designsystem.LwbTheme
import info.lwb.lastread.LastReadViewModel
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Root activity hosting the composable navigation graph.
 *
 * Responsibilities:
 * - Dispatch deep links (future enhancement) into the composable hierarchy.
 * - Provide a themed root surface and navigation host.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val theme by settingsVm.themeMode.collectAsState()
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.state.collectAsState()
    val lastReadVm: LastReadViewModel = hiltViewModel()
    val lastRead by lastReadVm.lastRead.collectAsState()
    val context = LocalContext.current
    val dark = when (theme) {
        ThemeMode.SYSTEM -> {
            isSystemInDarkTheme()
        }
        ThemeMode.LIGHT -> {
            false
        }
        ThemeMode.DARK -> {
            true
        }
    }
    LwbTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val signedIn = authState is info.lwb.auth.AuthUiState.SignedIn
            val userLabel = if (signedIn) {
                val u = (authState as info.lwb.auth.AuthUiState.SignedIn).user
                u.displayName ?: u.email ?: "Signed in"
            } else {
                null
            }
            // Show a one-time toast when authentication transitions to signed-in
            var wasSignedIn by remember { mutableStateOf(signedIn) }
            androidx.compose.runtime.LaunchedEffect(signedIn) {
                if (!wasSignedIn && signedIn) {
                    val u = (authState as info.lwb.auth.AuthUiState.SignedIn).user
                    val label = u.displayName ?: u.email ?: "Signed in"
                    Toast.makeText(context, "Signed in as $label", Toast.LENGTH_SHORT).show()
                }
                wasSignedIn = signedIn
            }
            AppNavHost(
                navController = navController,
                authVm = authVm,
                signedIn = signedIn,
                userLabel = userLabel,
                onSignOut = { authVm.signOut() },
                lastReadVm = lastReadVm,
                lastRead = lastRead,
            )
        }
    }
}

@Composable
private fun PasswordAuthSection(
    onRegister: (String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    enabled: Boolean = true,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Or use username & password:")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username or email") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            if (username.isNotBlank() && password.isNotBlank()) {
                onRegister(username.trim(), password)
            }
        }, enabled = enabled) { Text("Register") }
        Spacer(Modifier.height(6.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            if (username.isNotBlank() && password.isNotBlank()) {
                onLogin(username.trim(), password)
            }
        }, enabled = enabled) { Text("Login") }
    }
}

private object Destinations {
    const val HOME = "home"
    const val READER_BY_ID = "reader/{articleId}?indexUrl={indexUrl}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val LABEL_LIST = "label_list/{label}"
}

@Composable
private fun HomeDestination(
    navController: NavHostController,
    signedIn: Boolean,
    userLabel: String?,
    onSignOut: () -> Unit,
    lastRead: info.lwb.lastread.LastRead?,
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        HomeRoute(
            onItemClick = { _, label ->
                if (!label.isNullOrBlank()) {
                    val enc = URLEncoder.encode(label, Charsets.UTF_8.name())
                    navController.navigate("label_list/$enc")
                } else {
                    // Fallback: no label mapping yet
                }
            },
            onContinueReading = lastRead?.let { lr ->
                {
                    val enc = URLEncoder.encode(lr.indexUrl, Charsets.UTF_8.name())
                    navController.navigate("reader/${lr.articleId}?indexUrl=$enc")
                }
            },
            onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
            signedIn = signedIn,
            userLabel = userLabel,
            onSignOut = onSignOut,
        )
    }
}

@Composable
private fun ReaderByIdDestination(
    navController: NavHostController,
    backStackEntry: androidx.navigation.NavBackStackEntry,
    authVm: AuthViewModel,
    signedIn: Boolean,
    userLabel: String?,
    onSignOut: () -> Unit,
    lastReadVm: LastReadViewModel,
) {
    val id = backStackEntry.arguments?.getString("articleId")
    val rawIndexUrl = backStackEntry.arguments?.getString("indexUrl")
    val decodedIndexUrl = rawIndexUrl?.let {
        kotlin.runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull()
    }
    // Login dialog state handled in host
    var showLogin by remember { mutableStateOf(false) }
    val authState by authVm.state.collectAsState()
    // Auto-close dialog on successful sign-in
    if (showLogin && authState is info.lwb.auth.AuthUiState.SignedIn) {
        showLogin = false
    }
    if (id != null) {
        // Persist this as the last-read article whenever we navigate to it
        if (!decodedIndexUrl.isNullOrBlank()) {
            androidx.compose.runtime.LaunchedEffect(id, decodedIndexUrl) {
                lastReadVm.save(id, decodedIndexUrl)
            }
        }
        ReaderByIdRoute(
            articleId = id,
            navIndexUrl = decodedIndexUrl,
            onNavigateBack = { navController.popBackStack() },
            onRequireLogin = { showLogin = true },
            signedIn = signedIn,
            userLabel = userLabel,
            onSignOut = onSignOut,
        )
        if (showLogin) {
            LoginDialog(
                authVm = authVm,
                authState = authState,
                onDismiss = { showLogin = false },
            )
        }
    }
}

@Composable
private fun LoginDialog(
    authVm: AuthViewModel,
    authState: info.lwb.auth.AuthUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        val activity = (context as? android.app.Activity)
                        if (activity != null) {
                            authVm.signIn { activity }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = authState !is info.lwb.auth.AuthUiState.Loading,
                ) { Text("Continue with Google") }
                Spacer(Modifier.height(12.dp))
                PasswordAuthSection(
                    onRegister = { username, password ->
                        val activity = (context as? android.app.Activity)
                        if (activity != null) {
                            authVm.passwordRegister({ activity }, username, password)
                        }
                    },
                    onLogin = { username, password ->
                        authVm.passwordLogin(username, password)
                    },
                    enabled = authState !is info.lwb.auth.AuthUiState.Loading,
                )
                when (val s = authState) {
                    is info.lwb.auth.AuthUiState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                    is info.lwb.auth.AuthUiState.RegionBlocked -> {
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                    is info.lwb.auth.AuthUiState.Loading -> {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator()
                    }
                    else -> {
                        // No-op
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LabelListDestination(
    navController: NavHostController,
    backStackEntry: androidx.navigation.NavBackStackEntry,
) {
    val label = backStackEntry.arguments?.getString("label") ?: ""
    val decoded = URLDecoder.decode(label, Charsets.UTF_8.name())
    ArticlesListRoute(
        label = decoded,
        onArticleClick = { article ->
            val id = article.id.ifBlank { article.slug }
            val enc = URLEncoder.encode(article.indexUrl, Charsets.UTF_8.name())
            navController.navigate("reader/$id?indexUrl=$enc")
        },
        onNavigateBack = { navController.popBackStack() },
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    authVm: AuthViewModel,
    signedIn: Boolean,
    userLabel: String?,
    onSignOut: () -> Unit,
    lastReadVm: LastReadViewModel,
    lastRead: info.lwb.lastread.LastRead?,
) =
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
    ) {
        composable(Destinations.HOME) { HomeDestination(navController, signedIn, userLabel, onSignOut, lastRead) }
        composable(Destinations.READER_BY_ID) { backStackEntry ->
            ReaderByIdDestination(navController, backStackEntry, authVm, signedIn, userLabel, onSignOut, lastReadVm)
        }
        composable(Destinations.SEARCH) { SearchRoute() }
        composable(Destinations.SETTINGS) { SettingsRoute(onBack = { navController.popBackStack() }) }
        composable(Destinations.LABEL_LIST) { backStackEntry ->
            LabelListDestination(navController, backStackEntry)
        }
    }
