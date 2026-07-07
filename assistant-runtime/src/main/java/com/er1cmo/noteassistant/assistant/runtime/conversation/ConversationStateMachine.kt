package com.er1cmo.noteassistant.assistant.runtime.conversation

import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import javax.inject.Inject

class ConversationStateMachine @Inject constructor() {
    fun enable(current: AssistantState, nowMillis: Long): AssistantState = current.copy(
        phase = AssistantPhase.Idle,
        connection = AssistantConnectionStatus.Disconnected,
        activation = AssistantActivationStatus.Unknown,
        audio = AssistantAudioStatus.Idle,
        statusText = "助手已启用，等待连接",
        errorMessage = null,
        lastEventAt = nowMillis,
        sessionId = null,
        reconnectAttempt = 0,
        assistantEnabled = true,
    )

    fun disable(current: AssistantState, nowMillis: Long): AssistantState = current.copy(
        phase = AssistantPhase.Disabled,
        connection = AssistantConnectionStatus.Disconnected,
        audio = AssistantAudioStatus.Idle,
        statusText = "助手已关闭",
        errorMessage = null,
        lastEventAt = nowMillis,
        sessionId = null,
        reconnectAttempt = 0,
        assistantEnabled = false,
    )

    fun connecting(current: AssistantState, nowMillis: Long): AssistantState {
        if (!current.assistantEnabled) return error(current, "助手未启用，不能连接", nowMillis)
        return current.copy(
            phase = AssistantPhase.Connecting,
            connection = AssistantConnectionStatus.Connecting,
            statusText = "正在连接助手运行时",
            errorMessage = null,
            lastEventAt = nowMillis,
        )
    }

    fun connected(current: AssistantState, sessionId: String, nowMillis: Long): AssistantState {
        if (!current.assistantEnabled) return error(current, "助手未启用，不能进入连接态", nowMillis)
        return current.copy(
            phase = AssistantPhase.Connected,
            connection = AssistantConnectionStatus.Connected,
            activation = AssistantActivationStatus.Activated,
            audio = AssistantAudioStatus.Idle,
            statusText = "助手已连接（Fake Runtime）",
            errorMessage = null,
            lastEventAt = nowMillis,
            sessionId = sessionId,
        )
    }

    fun textSubmitted(current: AssistantState, text: String, nowMillis: Long): AssistantState {
        if (!current.assistantEnabled) return error(current, "助手未启用，不能发送文本", nowMillis)
        if (current.connection != AssistantConnectionStatus.Connected) return error(current, "助手未连接，不能发送文本", nowMillis)
        return current.copy(
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = "已发送文本，等待助手回复",
            errorMessage = null,
            lastUserText = text,
            lastEventAt = nowMillis,
        )
    }

    fun assistantText(current: AssistantState, text: String, nowMillis: Long): AssistantState = current.copy(
        phase = AssistantPhase.Connected,
        audio = AssistantAudioStatus.Idle,
        statusText = "收到助手文本回复",
        errorMessage = null,
        lastAssistantText = text,
        lastEventAt = nowMillis,
    )

    fun startListening(current: AssistantState, nowMillis: Long): AssistantState {
        if (!current.assistantEnabled) return error(current, "助手未启用，不能开始录音", nowMillis)
        if (current.connection != AssistantConnectionStatus.Connected) return error(current, "助手未连接，不能开始录音", nowMillis)
        return current.copy(
            phase = AssistantPhase.Listening,
            audio = AssistantAudioStatus.Recording,
            statusText = "按住说话中（Fake Audio）",
            errorMessage = null,
            lastEventAt = nowMillis,
        )
    }

    fun stopListening(current: AssistantState, nowMillis: Long): AssistantState {
        if (current.phase != AssistantPhase.Listening && current.audio != AssistantAudioStatus.Recording) {
            return current.copy(statusText = "当前没有录音需要停止", lastEventAt = nowMillis)
        }
        return current.copy(
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = "录音已停止，等待助手回复",
            lastEventAt = nowMillis,
        )
    }

    fun speaking(current: AssistantState, nowMillis: Long): AssistantState = current.copy(
        phase = AssistantPhase.Speaking,
        audio = AssistantAudioStatus.Playing,
        statusText = "助手正在播放回复（Fake TTS）",
        errorMessage = null,
        lastEventAt = nowMillis,
    )

    fun error(current: AssistantState, message: String, nowMillis: Long): AssistantState = current.copy(
        phase = AssistantPhase.Error,
        audio = if (current.audio == AssistantAudioStatus.Recording) AssistantAudioStatus.Error else current.audio,
        statusText = "助手运行时错误",
        errorMessage = message,
        lastEventAt = nowMillis,
    )
}
