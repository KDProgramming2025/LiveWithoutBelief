/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.model.Article

@Composable
fun SearchRoute(vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    SearchScreen(query = query, results = results, onQueryChange = vm::onQueryChange)
}

@Composable
fun SearchScreen(query: String, results: List<Article>, onQueryChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        var tf by remember { mutableStateOf(TextFieldValue(query)) }
        OutlinedTextField(
            value = tf,
            onValueChange = {
                tf = it
                onQueryChange(it.text)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search articles") },
        )
        Spacer(Modifier.height(12.dp))
        if (results.isEmpty() && query.isNotBlank()) {
            Text("No results", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results) { a ->
                ListItem(
                    headlineContent = { Text(a.title) },
                    supportingContent = { Text(a.updatedAt) },
                )
                Divider()
            }
        }
    }
}
