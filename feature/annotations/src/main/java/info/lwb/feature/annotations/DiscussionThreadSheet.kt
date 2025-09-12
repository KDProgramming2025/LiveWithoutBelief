/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.annotations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.core.domain.AddThreadMessageUseCase
import info.lwb.core.domain.GetThreadMessagesUseCase
import info.lwb.core.model.ThreadMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun DiscussionThreadSheet(annotationId: String, vm: DiscussionViewModel = hiltViewModel()) {
    LaunchedEffect(annotationId) { vm.load(annotationId) }
    val ui by vm.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Discussion", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(ui.messages) { m ->
                Text("${'$'}{m.type}: ${'$'}{m.contentRef}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type message or paste URI...") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    val type = if (text.startsWith("content://") || text.startsWith("http")) "image" else "text"
                    vm.send(type, text)
                    input = ""
                }
            }) { Text("Send") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tip: paste a URI for image/audio/pdf attachments", style = MaterialTheme.typography.labelSmall)
    }
}

data class DiscussionUiState(val annotationId: String = "", val messages: List<ThreadMessage> = emptyList())

@HiltViewModel
class DiscussionViewModel @Inject constructor(
    private val getMessages: GetThreadMessagesUseCase,
    private val addMessage: AddThreadMessageUseCase,
) : androidx.lifecycle.ViewModel() {
    private val annotationIdState = androidx.lifecycle.MutableLiveData("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DiscussionUiState> = annotationIdState.asFlow()
        .flatMapLatest { id ->
            if (id.isBlank()) {
                kotlinx.coroutines.flow.flowOf(DiscussionUiState())
            } else {
                getMessages(id).map { res ->
                    val list = (res as? info.lwb.core.common.Result.Success)?.data ?: emptyList()
                    DiscussionUiState(id, list)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscussionUiState())

    fun load(annotationId: String) {
        annotationIdState.value = annotationId
    }

    fun send(type: String, contentRef: String) {
        val id = uiState.value.annotationId
        if (id.isBlank()) return
        viewModelScope.launch { addMessage(id, type, contentRef) }
    }
}
