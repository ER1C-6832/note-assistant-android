package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode
import kotlinx.coroutines.flow.StateFlow

interface AssistantController {
    val state: StateFlow<AssistantState>

    suspend fun enableAssistant()
    suspend fun disableAssistant()
    suspend fun useFakeRuntime()
    suspend fun useRealRuntime()
    suspend fun connect()
    suspend fun reconnect()
    suspend fun disconnect(reason: String = "user_close")
    suspend fun sendText(text: String)

    suspend fun setVoiceInteractionMode(mode: VoiceInteractionMode)
    suspend fun setStreamingBargeInEnabled(enabled: Boolean)
    suspend fun startPushToTalk(hasRecordAudioPermission: Boolean)
    suspend fun stopPushToTalk()
    suspend fun startStreamingConversation(
        hasRecordAudioPermission: Boolean,
        source: AssistantEntrySource = AssistantEntrySource.StreamingButton,
        wakeKeyword: String? = null,
    )
    suspend fun stopStreamingConversation(reason: String = "user_stop")

    suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String = "{}")
    suspend fun simulateIncomingToolsList()

    suspend fun ensureDeviceIdentity()
    suspend fun resetDeviceIdentity()
    suspend fun runFakeActivation()
    suspend fun runRealActivation()

    suspend fun simulateConnectionClosed(code: Int = 1006, reason: String = "debug_abnormal_close")
    suspend fun simulateConnectionFailure(message: String = "debug_transport_failure")
    suspend fun simulateAudioFailure(message: String = "debug_audio_failure")
}
