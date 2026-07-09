package com.er1cmo.noteassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallConfirmationRequest
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallEventStore
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UiCommandViewModel @Inject constructor(
    uiCommandBus: UiCommandBus,
    private val mcpProtocolClient: McpProtocolClient,
    toolCallEventStore: ToolCallEventStore,
) : ViewModel() {
    val commands: SharedFlow<UiCommand> = uiCommandBus.commands
    val toolCallState: StateFlow<ToolCallUiState> = toolCallEventStore.state
    val confirmationRequests: SharedFlow<ToolCallConfirmationRequest> = toolCallEventStore.confirmationRequests

    fun confirmPendingCommand(confirmationId: String) {
        if (confirmationId.isBlank()) return
        val argumentsJson = confirmationArgumentsJson(confirmationId)
        viewModelScope.launch(Dispatchers.IO) {
            mcpProtocolClient.handleToolCall("assistant.confirm", argumentsJson)
        }
    }

    fun rejectPendingCommand(confirmationId: String) {
        if (confirmationId.isBlank()) return
        val argumentsJson = confirmationArgumentsJson(confirmationId)
        viewModelScope.launch(Dispatchers.IO) {
            mcpProtocolClient.handleToolCall("assistant.reject", argumentsJson)
        }
    }
}

private fun confirmationArgumentsJson(confirmationId: String): String {
    return "{\"confirmation_id\":\"${confirmationId.escapeJson()}\"}"
}

private fun String.escapeJson(): String = buildString {
    for (char in this@escapeJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
