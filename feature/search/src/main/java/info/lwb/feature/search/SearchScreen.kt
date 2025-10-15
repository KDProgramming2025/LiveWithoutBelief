/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.model.Article

/**
 * Entry point composable for the search feature.
 * Obtains a [SearchViewModel] via Hilt, collects state flows and delegates rendering to [SearchScreen].
 */
@Composable
fun SearchRoute(vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    SearchScreen(query = query, results = results, onQueryChange = vm::onQueryChange)
}

/**
 * Stateless search UI surface.
 *
 * @param query Current search text.
 * @param results Articles matching the query.
 * @param onQueryChange Callback invoked when user edits the search field.
 */
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
                HorizontalDivider()
            }
        }
    }
}
