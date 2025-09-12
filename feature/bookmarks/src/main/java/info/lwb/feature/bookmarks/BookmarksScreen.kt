/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.bookmarks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.domain.AddBookmarkUseCase
import info.lwb.core.domain.CreateFolderUseCase
import info.lwb.core.domain.GetBookmarkFoldersUseCase
import info.lwb.core.domain.GetBookmarksUseCase
import info.lwb.core.domain.MoveBookmarkUseCase
import info.lwb.core.domain.RemoveBookmarkUseCase
import info.lwb.core.domain.SearchBookmarkedUseCase
import info.lwb.core.model.Bookmark
import info.lwb.core.model.BookmarkFolder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getBookmarks: GetBookmarksUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val removeBookmark: RemoveBookmarkUseCase,
    private val getFolders: GetBookmarkFoldersUseCase,
    private val createFolder: CreateFolderUseCase,
    private val moveBookmark: MoveBookmarkUseCase,
    private val searchBookmarked: SearchBookmarkedUseCase,
) : androidx.lifecycle.ViewModel() {
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set
    var folders by mutableStateOf<List<BookmarkFolder>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<info.lwb.core.model.Article>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            getBookmarks().collectLatest { res ->
                when (res) {
                    is Result.Success -> bookmarks = res.data
                    is Result.Error -> { /* TODO: expose error */ }
                    Result.Loading -> {}
                }
            }
        }
        viewModelScope.launch {
            getFolders().collectLatest { res ->
                when (res) {
                    is Result.Success -> folders = res.data
                    is Result.Error -> { }
                    Result.Loading -> {}
                }
            }
        }
    }

    fun add(articleId: String) {
        viewModelScope.launch { addBookmark(articleId, folderId = null) }
    }

    fun remove(b: Bookmark) {
        viewModelScope.launch { removeBookmark(b.id) }
    }

    fun newFolder(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val id = (createFolder(name) as? Result.Success)?.data
            if (id != null) onCreated(id)
        }
    }

    fun move(b: Bookmark, folderId: String?) {
        viewModelScope.launch { moveBookmark(b.id, folderId) }
    }

    fun search(query: String) {
        viewModelScope.launch { searchResults = searchBookmarked(query) }
    }
}

@Composable
fun BookmarksScreen(vm: BookmarksViewModel) {
    val list = vm.bookmarks
    val folders = vm.folders
    val results = vm.searchResults
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        var newArticleId by remember { mutableStateOf("") }
        var newFolderName by remember { mutableStateOf("") }
        var query by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newArticleId,
                onValueChange = { newArticleId = it },
                modifier = Modifier.weight(1f),
                label = { Text("Article ID") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (newArticleId.isNotBlank()) vm.add(newArticleId) }) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New folder") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (newFolderName.isNotBlank()) vm.newFolder(newFolderName) { newFolderName = "" } }) {
                Text("Create Folder")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                vm.search(query)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search bookmarked articles") },
        )
        Spacer(Modifier.height(12.dp))
        if (results.isNotEmpty()) {
            Text("Results", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                items(results) { a -> Text("- ${'$'}{a.title}") }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (folders.isNotEmpty()) {
            Text("Folders", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                folders.forEach { f ->
                    AssistChip(onClick = { /* future: filter */ }, label = { Text(f.name) })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list) { b ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("• ${'$'}{b.articleId}")
                        if (folders.isNotEmpty()) {
                            Row {
                                folders.forEach { f ->
                                    AssistChip(onClick = { vm.move(b, f.id) }, label = { Text("Move→ ${'$'}{f.name}") })
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { vm.remove(b) }) { Text("Remove") }
                }
                Divider()
            }
        }
    }
}
