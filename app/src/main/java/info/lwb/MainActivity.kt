/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.lwb.auth.AuthViewModel
import info.lwb.feature.bookmarks.BookmarksRoute
import info.lwb.feature.home.HomeRoute
import info.lwb.feature.reader.ArticleListByLabelRoute
import info.lwb.feature.reader.ReaderByIdRoute
import info.lwb.feature.search.SearchRoute
import info.lwb.feature.settings.SettingsRoute
import info.lwb.feature.settings.SettingsViewModel
import info.lwb.feature.settings.ThemeMode
import info.lwb.ui.designsystem.LwbTheme
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
        setContent { appRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setContent { appRoot() }
    }
}

@Composable
private fun appRoot() {
    val navController = rememberNavController()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val theme by settingsVm.themeMode.collectAsState()
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
            hiltViewModel<AuthViewModel>()
            appNavHost(navController)
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
        Button(onClick = {
            if (email.isNotBlank()) {
                onSubmit(email.trim())
            }
        }) { Text("Send Link") }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun RegionBlockedBanner(message: String = "Google sign-in blocked here.") {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Start,
        )
    }
}

// Password auth UI removed.
@Composable
private fun PasswordAuthSection(onRegister: (String, String) -> Unit, onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Or use username & password:")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username or email") })
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
    Spacer(Modifier.height(8.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = {
            if (username.isNotBlank() && password.isNotBlank()) {
                onRegister(username.trim(), password)
            }
        }) { Text("Register") }
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            if (username.isNotBlank() && password.isNotBlank()) {
                onLogin(username.trim(), password)
            }
        }) { Text("Login") }
    }
}

private object Destinations {
    const val HOME = "home"
    const val READER = "reader"
    const val READER_BY_ID = "reader/{articleId}"
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"
    const val LABEL_LIST = "label_list/{label}"
}

@Composable
private fun appNavHost(navController: NavHostController) =
    NavHost(navController = navController, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) {
            Box(Modifier.fillMaxSize()) {
                HomeRoute(
                    onItemClick = { _, label ->
                        if (!label.isNullOrBlank()) {
                            val enc = URLEncoder.encode(label, Charsets.UTF_8.name())
                            navController.navigate("label_list/$enc")
                        } else {
                            // Fallback: no label mapping yet
                        }
                    },
                    onContinueReading = {
                        // Placeholder: navigate to Reader screen (later, restore last position)
                        navController.navigate(Destinations.READER)
                    },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                )
            }
        }
        composable(Destinations.READER) {
            ReaderByIdRoute(
                articleId = "sample-1",
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.READER_BY_ID) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("articleId")
            if (id != null) {
                ReaderByIdRoute(
                    articleId = id,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
        composable(Destinations.SEARCH) { SearchRoute() }
        composable(Destinations.BOOKMARKS) { BookmarksRoute() }
        composable(Destinations.SETTINGS) { SettingsRoute(onBack = { navController.popBackStack() }) }
        composable(Destinations.LABEL_LIST) { backStackEntry ->
            val label = backStackEntry.arguments?.getString("label") ?: ""
            ArticleListByLabelRoute(
                label = URLDecoder.decode(label, Charsets.UTF_8.name()),
                onArticleClick = { article ->
                    // Prefer slug if stable; else id
                    val id = article.id.ifBlank { article.slug }
                    navController.navigate("reader/$id")
                },
                onNavigateHome = { navController.popBackStack(Destinations.HOME, inclusive = false) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
