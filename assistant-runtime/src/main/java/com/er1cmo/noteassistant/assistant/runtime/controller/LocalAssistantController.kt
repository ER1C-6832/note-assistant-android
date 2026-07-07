package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.conversation.ConversationStateMachine
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class LocalAssistantController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val stateMachine: ConversationStateMachine,
    private val mcpProtocolClient: McpProtocolClient,
) : AssistantController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AssistantState.disabled(nowMillis()))

    override val state: StateFlow<AssistantState> = mutableState.asStateFlow()

    init {
        scope.launch {
            settingsRepository.assistantEnabled.collect { enabled ->
                val current = mutableState.value
                mutableState.value = if (enabled) {
                    if (current.assistantEnabled) current else stateMachine.enable(current, nowMillis())
                } else {
                    if (!current.assistantEnabled) current else stateMachine.disable(current, nowMillis())
                }
            }
        }
    }

    override suspend fun enableAssistant() {
        settingsRepository.setAssistantEnabled(true)
        mutableState.value = stateMachine.enable(mutableState.value, nowMillis())
    }

    override suspend fun disableAssistant() {
        settingsRepository.setAssistantEnabled(false)
        mutableState.value = stateMachine.disable(mutableState.value, nowMillis())
    }

    override suspend fun connect() {
        val connecting = stateMachine.connecting(mutableState.value, nowMillis())
        mutableState.value = connecting
        if (!connecting.assistantEnabled || connecting.errorMessage != null) return
        delay(120)
        mutableState.value = stateMachine.connected(connecting, sessionId = fakeSessionId(), nowMillis = nowMillis())
    }

    override suspend fun disconnect(reason: String) {
        val current = mutableState.value
        mutableState.value = current.copy(
            phase = if (current.assistantEnabled) com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase.Idle else com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase.Disabled,
            connection = com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus.Disconnected,
            audio = com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus.Idle,
            statusText = "助手连接已关闭：$reason",
            errorMessage = null,
            sessionId = null,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            mutableState.value = stateMachine.error(mutableState.value, "文本不能为空", nowMillis())
            return
        }
        if (!mutableState.value.isConnected) connect()
        val thinking = stateMachine.textSubmitted(mutableState.value, trimmed, nowMillis())
        mutableState.value = thinking
        if (thinking.errorMessage != null) return
        delay(180)
        mutableState.value = stateMachine.assistantText(
            current = thinking,
            text = "Fake Runtime：已收到『$trimmed』。Phase3 当前不会执行便签工具。",
            nowMillis = nowMillis(),
        )
    }

    override suspend fun startPushToTalk(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) {
            mutableState.value = stateMachine.error(mutableState.value, "缺少 RECORD_AUDIO 权限，未开始录音", nowMillis())
            return
        }
        if (!mutableState.value.isConnected) connect()
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis())
    }

    override suspend fun stopPushToTalk() {
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        mutableState.value = stopped
        if (stopped.errorMessage != null) return
        delay(120)
        val speaking = stateMachine.speaking(stopped, nowMillis())
        mutableState.value = speaking.copy(lastAssistantText = "Fake Runtime：语音输入已结束，播放一段模拟回复。")
        delay(120)
        mutableState.value = stateMachine.assistantText(
            current = mutableState.value,
            text = "Fake Runtime：语音回复完成。",
            nowMillis = nowMillis(),
        )
    }

    override suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String) {
        val result = mcpProtocolClient.handleToolCall(toolName, argumentsJson)
        mutableState.value = mutableState.value.copy(
            lastAssistantText = result.message,
            statusText = "已阻断或处理工具调用：${result.toolName}",
            lastEventAt = nowMillis(),
        )
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private fun fakeSessionId(): String = "phase3-fake-${nowMillis()}"
}
