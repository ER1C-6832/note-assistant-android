package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationClient
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationState
import com.er1cmo.noteassistant.assistant.runtime.audio.RealAudioEngine
import com.er1cmo.noteassistant.assistant.runtime.conversation.ConversationStateMachine
import com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentityManager
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.network.FakeXiaozhiWebSocketClient
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiConnectionConfig
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class LocalAssistantController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val stateMachine: ConversationStateMachine,
    private val mcpProtocolClient: McpProtocolClient,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val otaActivationClient: OtaActivationClient,
    private val fakeWebSocketClient: FakeXiaozhiWebSocketClient,
    private val realAudioEngine: RealAudioEngine,
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
        realAudioEngine.cancel()
        fakeWebSocketClient.close("assistant_disabled")
        mutableState.value = stateMachine.disable(mutableState.value, nowMillis()).copy(
            connection = AssistantConnectionStatus.Disconnected,
            audio = AssistantAudioStatus.Idle,
            sessionId = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = null,
        )
    }

    override suspend fun connect() {
        val connecting = stateMachine.connecting(mutableState.value, nowMillis())
        mutableState.value = connecting.copy(statusText = "正在连接 Fake Xiaozhi WebSocket")
        if (!connecting.assistantEnabled || connecting.errorMessage != null) return

        val identity = deviceIdentityManager.ensureIdentity()
        val config = XiaozhiConnectionConfig(
            websocketUrl = settingsRepository.websocketUrl.first(),
            websocketToken = settingsRepository.assistantWebsocketToken.first().ifBlank { "phase3-fake-token" },
            deviceId = identity.deviceId,
            clientId = identity.clientId,
        )
        val result = fakeWebSocketClient.connect(config)
        if (result.success && !result.sessionId.isNullOrBlank()) {
            mutableState.value = stateMachine.connected(
                current = connecting,
                sessionId = result.sessionId,
                nowMillis = nowMillis(),
            ).copy(
                deviceId = identity.deviceId,
                clientId = identity.clientId,
                websocketUrl = result.websocketUrl,
                lastClientJson = result.outgoingHelloJson,
                lastServerJson = result.incomingHelloJson,
                lastProtocolEvent = result.event.javaClass.simpleName,
                statusText = "Fake WebSocket 已完成 hello/session 握手",
            )
        } else {
            mutableState.value = stateMachine.error(
                current = mutableState.value,
                message = "Fake WebSocket hello 失败",
                nowMillis = nowMillis(),
            ).copy(
                lastClientJson = result.outgoingHelloJson,
                lastServerJson = result.incomingHelloJson,
                lastProtocolEvent = result.event.javaClass.simpleName,
            )
        }
    }

    override suspend fun disconnect(reason: String) {
        realAudioEngine.cancel()
        fakeWebSocketClient.close(reason)
        val current = mutableState.value
        mutableState.value = current.copy(
            phase = if (current.assistantEnabled) AssistantPhase.Idle else AssistantPhase.Disabled,
            connection = AssistantConnectionStatus.Disconnected,
            audio = AssistantAudioStatus.Idle,
            statusText = "助手连接已关闭：$reason",
            errorMessage = null,
            sessionId = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = null,
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

        val turn = fakeWebSocketClient.sendText(trimmed)
        if (!turn.success) {
            mutableState.value = stateMachine.error(
                current = thinking,
                message = turn.message,
                nowMillis = nowMillis(),
            )
            return
        }
        delay(80)
        val assistantText = when (val event = turn.event) {
            is ProtocolEvent.AssistantText -> event.text
            else -> turn.message
        }
        mutableState.value = stateMachine.assistantText(
            current = thinking,
            text = assistantText,
            nowMillis = nowMillis(),
        ).copy(
            lastClientJson = turn.outgoingJson,
            lastServerJson = turn.incomingJson,
            lastProtocolEvent = turn.event.javaClass.simpleName,
            statusText = "Fake WebSocket 文本回合完成",
        )
    }

    override suspend fun startPushToTalk(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) {
            realAudioEngine.cancel()
            mutableState.value = stateMachine.error(
                current = mutableState.value.copy(audio = AssistantAudioStatus.Error),
                message = "缺少 RECORD_AUDIO 权限，未开始录音",
                nowMillis = nowMillis(),
            ).copy(
                statusText = "麦克风权限被拒绝，真实 AudioRecord 未启动",
                lastAudioSummary = "未获得 RECORD_AUDIO，未创建 AudioRecord / MediaCodec OpusEncoder",
            )
            return
        }
        if (!mutableState.value.isConnected) connect()
        if (!mutableState.value.isConnected) return

        val listenStart = fakeWebSocketClient.sendStartListening(mode = "manual")
        if (!listenStart.success) {
            mutableState.value = stateMachine.error(
                current = mutableState.value,
                message = listenStart.message,
                nowMillis = nowMillis(),
            )
            return
        }
        val audioStart = realAudioEngine.startRecording(nowMillis()) { opusFrame ->
            fakeWebSocketClient.sendAudioFrame(opusFrame).success
        }
        if (!audioStart.started && !realAudioEngine.isRecording()) {
            mutableState.value = stateMachine.error(
                current = mutableState.value,
                message = audioStart.summary,
                nowMillis = nowMillis(),
            )
            return
        }
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis()).copy(
            phase = AssistantPhase.Listening,
            audio = AssistantAudioStatus.Recording,
            statusText = "真实 AudioRecord / MediaCodec Opus 已启动，松开后必须在 300ms 内停止",
            errorMessage = null,
            lastClientJson = listenStart.outgoingJson,
            lastServerJson = listenStart.incomingJson,
            lastProtocolEvent = listenStart.event.javaClass.simpleName,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = audioStart.summary,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun stopPushToTalk() {
        val stoppedAudio = realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 300L)
        val uploaded = fakeWebSocketClient.uploadedAudioFrameCount()
        val listenStop = fakeWebSocketClient.sendStopListening()
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        val baseSummary = buildString {
            append(stoppedAudio.summary)
            append("；AudioRecord processing=${stoppedAudio.processingSummary}")
        }
        mutableState.value = stopped.copy(
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = if (stoppedAudio.stoppedWithinBudget) {
                "真实音频已在 ${stoppedAudio.stopLatencyMs}ms 内停止，等待助手回复"
            } else {
                "真实音频停止超时：${stoppedAudio.stopLatencyMs}ms"
            },
            errorMessage = stoppedAudio.errorMessage ?: if (stoppedAudio.stoppedWithinBudget) null else "PTT release 后停止超过 300ms",
            lastClientJson = listenStop.outgoingJson,
            lastServerJson = listenStop.incomingJson,
            lastProtocolEvent = listenStop.event.javaClass.simpleName,
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = uploaded,
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = baseSummary,
            lastEventAt = nowMillis(),
        )
        if (!stoppedAudio.stoppedWithinBudget || stoppedAudio.errorMessage != null) return

        delay(80)
        val speaking = stateMachine.speaking(mutableState.value, nowMillis())
        mutableState.value = speaking.copy(
            lastAssistantText = "真实音频上行完成：已上传 $uploaded 帧 Opus，开始用 loopback 模拟服务端二进制音频下行。",
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = uploaded,
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = baseSummary,
        )

        val playback = realAudioEngine.playOpusFrames(stoppedAudio.encodedFrames)
        delay(80)
        mutableState.value = stateMachine.assistantText(
            current = mutableState.value,
            text = if (playback.success) {
                "Real Audio：Opus 上行和 AudioTrack 下行播放验证完成。PTT release->stop=${stoppedAudio.stopLatencyMs}ms。"
            } else {
                "Real Audio：上行完成，但下行播放未成功：${playback.summary}"
            },
            nowMillis = nowMillis(),
        ).copy(
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = uploaded,
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = "$baseSummary；${playback.summary}",
            lastServerJson = "{\"type\":\"binary_audio_loopback\",\"opus_frames\":${playback.opusFrames},\"pcm_frames\":${playback.pcmFrames},\"pcm_bytes\":${playback.pcmBytes}}",
            lastProtocolEvent = "BinaryAudioLoopback",
        )
    }

    override suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String) {
        if (!mutableState.value.isConnected) connect()
        val turn = fakeWebSocketClient.simulateIncomingToolCall(toolName, argumentsJson)
        mutableState.value = mutableState.value.copy(
            lastAssistantText = turn.message,
            statusText = if (turn.success) "MCP tools/call 已安全处理：${toolName}" else turn.message,
            lastServerJson = turn.incomingJson,
            lastClientJson = turn.outgoingResponseJson,
            lastProtocolEvent = turn.event.javaClass.simpleName,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun ensureDeviceIdentity() {
        val identity = deviceIdentityManager.ensureIdentity()
        mutableState.value = mutableState.value.copy(
            deviceId = identity.deviceId,
            clientId = identity.clientId,
            statusText = "设备身份已准备：${identity.displayDeviceId}",
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun resetDeviceIdentity() {
        realAudioEngine.cancel()
        fakeWebSocketClient.close("identity_reset")
        val identity = deviceIdentityManager.resetIdentity()
        mutableState.value = mutableState.value.copy(
            phase = if (mutableState.value.assistantEnabled) AssistantPhase.Idle else AssistantPhase.Disabled,
            activation = AssistantActivationStatus.Unknown,
            connection = AssistantConnectionStatus.Disconnected,
            audio = AssistantAudioStatus.Idle,
            sessionId = null,
            deviceId = identity.deviceId,
            clientId = identity.clientId,
            activationCode = null,
            websocketUrl = null,
            lastClientJson = null,
            lastServerJson = null,
            lastProtocolEvent = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = null,
            statusText = "设备身份已重置：${identity.displayDeviceId}，需要重新激活。",
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun runFakeActivation() {
        if (!mutableState.value.assistantEnabled) enableAssistant()
        mutableState.value = mutableState.value.copy(
            phase = AssistantPhase.Activating,
            activation = AssistantActivationStatus.Activating,
            statusText = "Fake activation 执行中",
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
        runCatching { otaActivationClient.runFakeActivation() }
            .onSuccess { outcome ->
                mutableState.value = mutableState.value.copy(
                    phase = AssistantPhase.Idle,
                    activation = AssistantActivationStatus.Activated,
                    statusText = outcome.message,
                    errorMessage = null,
                    activationCode = outcome.activationCode,
                    websocketUrl = outcome.websocketUrl,
                    lastEventAt = nowMillis(),
                )
            }
            .onFailure { error ->
                mutableState.value = stateMachine.error(
                    current = mutableState.value.copy(activation = AssistantActivationStatus.Failed),
                    message = error.message ?: error::class.java.simpleName,
                    nowMillis = nowMillis(),
                )
            }
    }

    override suspend fun runRealActivation() {
        if (!mutableState.value.assistantEnabled) enableAssistant()
        mutableState.value = mutableState.value.copy(
            phase = AssistantPhase.Activating,
            activation = AssistantActivationStatus.Activating,
            statusText = "真实 OTA / activation 执行中",
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
        runCatching { otaActivationClient.runRealOtaAndActivation() }
            .onSuccess { outcome ->
                val activationStatus = when (outcome.state) {
                    OtaActivationState.Activated -> AssistantActivationStatus.Activated
                    OtaActivationState.Required -> AssistantActivationStatus.Required
                    OtaActivationState.Failed -> AssistantActivationStatus.Failed
                }
                mutableState.value = mutableState.value.copy(
                    phase = if (activationStatus == AssistantActivationStatus.Activated) AssistantPhase.Idle else AssistantPhase.Error,
                    activation = activationStatus,
                    statusText = outcome.message,
                    errorMessage = if (activationStatus == AssistantActivationStatus.Failed) outcome.message else null,
                    activationCode = outcome.activationCode,
                    websocketUrl = outcome.websocketUrl,
                    lastEventAt = nowMillis(),
                )
            }
            .onFailure { error ->
                mutableState.value = stateMachine.error(
                    current = mutableState.value.copy(activation = AssistantActivationStatus.Failed),
                    message = error.message ?: error::class.java.simpleName,
                    nowMillis = nowMillis(),
                )
            }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
