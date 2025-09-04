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
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.lwb.feature.reader.ReaderScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { appRoot() }
    }
}

@Composable
private fun appRoot() {
    val navController = rememberNavController()
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            appNavHost(navController)
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
