/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder

/**
 * High-level screen displaying bookmark management UI.
 *
 * Decomposed into smaller composables to satisfy complexity constraints.
 *
 * @param vm Active [BookmarksViewModel] providing state and user intent handlers.
 */
@Composable
fun BookmarksScreen(vm: BookmarksViewModel) {
    val list = vm.bookmarks
    val folders = vm.folders
    val results = vm.searchResults
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        AddBookmarkRow(onAdd = vm::add)
        Spacer(Modifier.height(8.dp))
        CreateFolderRow(onCreate = vm::newFolder)
        Spacer(Modifier.height(8.dp))
        BookmarksSearchField(onQuery = vm::search)
        Spacer(Modifier.height(12.dp))
        SearchResults(results)
        FolderChipsRow(folders)
        Spacer(Modifier.height(8.dp))
        BookmarksList(
            bookmarks = list,
            folders = folders,
            onMove = vm::move,
            onRemove = vm::remove,
        )
    }
}

@Composable
private fun AddBookmarkRow(onAdd: (String) -> Unit) {
    var newArticleId by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = newArticleId,
            onValueChange = { newArticleId = it },
            modifier = Modifier.weight(1f),
            label = { Text("Article ID") },
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (newArticleId.isNotBlank()) {
                    onAdd(newArticleId)
                }
            },
        ) { Text("Add") }
    }
}

@Composable
private fun CreateFolderRow(onCreate: (String, (String) -> Unit) -> Unit) {
    var newFolderName by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = newFolderName,
            onValueChange = { newFolderName = it },
            modifier = Modifier.weight(1f),
            label = { Text("New folder") },
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (newFolderName.isNotBlank()) {
                    onCreate(newFolderName) { newFolderName = "" }
                }
            },
        ) { Text("Create Folder") }
    }
}

@Composable
private fun BookmarksSearchField(onQuery: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            onQuery(query)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search bookmarked articles") },
    )
}

@Composable
private fun SearchResults(results: List<info.lwb.core.model.Article>) {
    if (results.isEmpty()) {
        return
    }
    Text("Results", style = MaterialTheme.typography.titleSmall)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
        items(results) { a ->
            Text("- ${'$'}{a.title}")
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FolderChipsRow(folders: List<BookmarkFolder>) {
    if (folders.isEmpty()) {
        return
    }
    Text("Folders", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth()) {
        folders.forEach { f ->
            AssistChip(onClick = { /* future: filter */ }, label = { Text(f.name) })
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun BookmarksList(
    bookmarks: List<Bookmark>,
    folders: List<BookmarkFolder>,
    onMove: (Bookmark, String?) -> Unit,
    onRemove: (Bookmark) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(bookmarks) { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("• ${'$'}{b.articleId}")
                    if (folders.isNotEmpty()) {
                        Row {
                            folders.forEach { f ->
                                AssistChip(
                                    onClick = { onMove(b, f.id) },
                                    label = { Text("Move→ ${'$'}{f.name}") },
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { onRemove(b) }) { Text("Remove") }
            }
            Divider()
        }
    }
}
