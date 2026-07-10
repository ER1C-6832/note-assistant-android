package com.er1cmo.noteassistant.assistant.runtime.state

import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwner

enum class AssistantPhase(val storageValue: String) {
    Idle("idle"),
    Disabled("disabled"),
    Activating("activating"),
    Connecting("connecting"),
    Connected("connected"),
    Listening("listening"),
    UploadingAudio("uploading_audio"),
    Thinking("thinking"),
    Speaking("speaking"),
    Reconnecting("reconnecting"),
    Error("error"),
}

enum class AssistantConnectionStatus(val storageValue: String) {
    Disconnected("disconnected"),
    Connecting("connecting"),
    Connected("connected"),
    Closing("closing"),
}

enum class AssistantActivationStatus(val storageValue: String) {
    Unknown("unknown"),
    Required("required"),
    Activating("activating"),
    Activated("activated"),
    Failed("failed"),
}

enum class AssistantAudioStatus(val storageValue: String) {
    Idle("idle"),
    Recording("recording"),
    Playing("playing"),
    Error("error"),
}

enum class AssistantRuntimeMode(val storageValue: String) {
    Fake("fake"),
    Real("real"),
}

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Disabled,
    val connection: AssistantConnectionStatus = AssistantConnectionStatus.Disconnected,
    val activation: AssistantActivationStatus = AssistantActivationStatus.Unknown,
    val audio: AssistantAudioStatus = AssistantAudioStatus.Idle,
    val statusText: String = "助手未启用",
    val errorMessage: String? = null,
    val lastUserText: String? = null,
    val lastAssistantText: String? = null,
    val lastEventAt: Long? = null,
    val sessionId: String? = null,
    val reconnectAttempt: Int = 0,
    val assistantEnabled: Boolean = false,
    val fakeRuntime: Boolean = false,
    val runtimeMode: AssistantRuntimeMode = AssistantRuntimeMode.Real,
    val deviceId: String? = null,
    val clientId: String? = null,
    val activationCode: String? = null,
    val websocketUrl: String? = null,
    val lastClientJson: String? = null,
    val lastServerJson: String? = null,
    val lastProtocolEvent: String? = null,
    val audioCapturedFrames: Int = 0,
    val audioEncodedFrames: Int = 0,
    val audioUploadedFrames: Int = 0,
    val pushToTalkStopLatencyMs: Long? = null,
    val lastAudioSummary: String? = null,
    val lastCloseCode: Int? = null,
    val lastCloseReason: String? = null,
    val lastReconnectDecision: String? = null,
    val runtimeErrorCount: Int = 0,
    val gateBRealHandshakeVerified: Boolean = false,
    val gateBRealTextVerified: Boolean = false,
    val gateBRealAudioUploadVerified: Boolean = false,
    val gateBRealAudioPlaybackVerified: Boolean = false,
    val phase4RealToolCallVerified: Boolean = false,
    val lastToolName: String? = null,
    val lastToolStatus: String? = null,
    val lastCommandLogId: Long? = null,
    val lastConfirmationId: String? = null,
    val preferredVoiceMode: VoiceInteractionMode = VoiceInteractionMode.StreamingConversation,
    val activeEntrySource: AssistantEntrySource? = null,
    val microphoneOwner: MicrophoneOwner = MicrophoneOwner.None,
    val streamingConversationState: StreamingConversationState = StreamingConversationState.Inactive,
    val streamingSessionActive: Boolean = false,
    val streamingSessionId: String? = null,
    val streamingTurnIndex: Int = 0,
    val streamingIdleTimeoutMs: Long = 15_000L,
    val vadState: VoiceActivityState = VoiceActivityState.Disabled,
    val vadStatusText: String = "VAD 未启用",
) {
    val isConnected: Boolean
        get() = connection == AssistantConnectionStatus.Connected && sessionId != null

    val isRealRuntime: Boolean
        get() = runtimeMode == AssistantRuntimeMode.Real

    @Deprecated(
        message = "Use phase4RealToolCallVerified. Phase4 tools are executed, not merely blocked.",
        replaceWith = ReplaceWith("phase4RealToolCallVerified"),
    )
    val gateBRealToolCallBlockedVerified: Boolean
        get() = phase4RealToolCallVerified

    companion object {
        fun disabled(nowMillis: Long? = null): AssistantState = AssistantState(
            phase = AssistantPhase.Disabled,
            connection = AssistantConnectionStatus.Disconnected,
            activation = AssistantActivationStatus.Unknown,
            audio = AssistantAudioStatus.Idle,
            statusText = "助手已关闭",
            lastEventAt = nowMillis,
            assistantEnabled = false,
        )

        fun idle(nowMillis: Long? = null): AssistantState = AssistantState(
            phase = AssistantPhase.Idle,
            connection = AssistantConnectionStatus.Disconnected,
            activation = AssistantActivationStatus.Unknown,
            audio = AssistantAudioStatus.Idle,
            statusText = "助手已启用，等待连接",
            lastEventAt = nowMillis,
            assistantEnabled = true,
        )
    }
}
