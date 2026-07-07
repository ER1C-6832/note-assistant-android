package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import kotlinx.coroutines.flow.StateFlow

interface AssistantController {
    val state: StateFlow<AssistantState>

    suspend fun enableAssistant()
    suspend fun disableAssistant()
    suspend fun connect()
    suspend fun disconnect(reason: String = "user_close")
    suspend fun sendText(text: String)
    suspend fun startPushToTalk(hasRecordAudioPermission: Boolean)
    suspend fun stopPushToTalk()
    suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String = "{}")
}
