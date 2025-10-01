/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.feature.annotations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
// Removed unused SpacerDefaults import
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Bottom sheet UI showing a discussion thread scoped to a specific annotation.
 *
 * Lifecycle:
 * - On first composition (or when [annotationId] changes) it triggers loading via [DiscussionViewModel.load].
 * - Renders a message list and an input field to send new messages or attachment references (URIs).
 *
 * @param annotationId The ID of the annotation whose discussion thread is shown.
 * @param vm Injected [DiscussionViewModel]; overridden in previews/tests if needed.
 */
@Composable
fun DiscussionThreadSheet(annotationId: String, vm: DiscussionViewModel = hiltViewModel()) {
    LaunchedEffect(annotationId) { vm.load(annotationId) }
    val ui by vm.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Discussion", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
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
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        val type = if (text.startsWith("content://") || text.startsWith("http")) {
                            "image"
                        } else {
                            "text"
                        }
                        vm.send(type, text)
                        input = ""
                    }
                },
            ) {
                Text("Send")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tip: paste a URI for image/audio/pdf attachments", style = MaterialTheme.typography.labelSmall)
    }
}

/** Immutable UI state for a discussion thread.
 * @property annotationId The currently loaded annotation id (blank if none loaded yet).
 * @property messages Ordered list of thread messages (newest-to-oldest or backend-defined order).
 */
data class DiscussionUiState(val annotationId: String = "", val messages: List<ThreadMessage> = emptyList())

/** ViewModel coordinating thread message loading and sending.
 *
 * Responsibilities:
 * - Maintain the current annotation context.
 * - Expose a [StateFlow] of [DiscussionUiState] that updates as messages stream in.
 * - Provide actions to load a new annotation thread and send new messages.
 */
@HiltViewModel
class DiscussionViewModel @Inject constructor(
    private val getMessages: GetThreadMessagesUseCase,
    private val addMessage: AddThreadMessageUseCase,
) : androidx.lifecycle.ViewModel() {
    private val annotationIdState = androidx.lifecycle.MutableLiveData("")

    /**
     * Backing reactive UI state flow.
     * Emits a blank state until an annotation id is set, then streams updated message lists.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DiscussionUiState> =
        annotationIdState
            .asFlow()
            .flatMapLatest { id ->
                if (id.isBlank()) {
                    kotlinx.coroutines.flow.flowOf(DiscussionUiState())
                } else {
                    getMessages(id).map { res ->
                        val list = (res as? info.lwb.core.common.Result.Success)?.data ?: emptyList()
                        DiscussionUiState(id, list)
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UI_STATE_SUBSCRIPTION_TIMEOUT_MS),
                initialValue = DiscussionUiState(),
            )

    /** Set the active annotation whose discussion thread should be observed. */
    fun load(annotationId: String) {
        annotationIdState.value = annotationId
    }

    /** Send a new message.
     * @param type Semantic message type (e.g. "text", "image").
     * @param contentRef Either inline text or a URI referencing external content.
     */
    fun send(type: String, contentRef: String) {
        val id = uiState.value.annotationId
        if (id.isBlank()) {
            return
        }
        viewModelScope.launch { addMessage(id, type, contentRef) }
    }
}

private const val UI_STATE_SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L
