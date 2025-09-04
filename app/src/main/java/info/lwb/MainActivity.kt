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
            var signedInState by remember { mutableStateOf(authFacade.currentUser()) }
            val scope = rememberCoroutineScope()
            Column {
                if (signedInState == null) {
                    Button(onClick = {
                        scope.launch {
                            authFacade.oneTapSignIn(this@MainActivity).onSuccess { user ->
                                signedInState = user
                            }
                        }
                    }) { Text("One-Tap Sign In") }
                } else {
                    Text("Hello ${signedInState?.displayName ?: signedInState?.email}")
                    Button(onClick = {
                        scope.launch {
                            authFacade.signOut()
                            signedInState = null
                        }
                    }) { Text("Sign Out") }
                    appNavHost(navController)
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
