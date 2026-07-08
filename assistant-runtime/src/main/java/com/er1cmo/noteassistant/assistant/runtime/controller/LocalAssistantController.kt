package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationClient
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationState
import com.er1cmo.noteassistant.assistant.runtime.audio.RealAudioEngine
import com.er1cmo.noteassistant.assistant.runtime.conversation.ConversationStateMachine
import com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentityManager
import com.er1cmo.noteassistant.assistant.runtime.network.FakeXiaozhiWebSocketClient
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiConnectionConfig
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiWebSocketClient
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiWebSocketEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.recovery.ReconnectPolicy
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantRuntimeMode
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
    private val deviceIdentityManager: DeviceIdentityManager,
    private val otaActivationClient: OtaActivationClient,
    private val fakeWebSocketClient: FakeXiaozhiWebSocketClient,
    private val realWebSocketClient: XiaozhiWebSocketClient,
    private val realAudioEngine: RealAudioEngine,
    private val reconnectPolicy: ReconnectPolicy,
) : AssistantController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AssistantState.disabled(nowMillis()))

    @Volatile
    private var realUploadedAudioFrames: Int = 0

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
        realWebSocketClient.close("assistant_disabled")
        mutableState.value = stateMachine.disable(mutableState.value, nowMillis()).copy(
            connection = AssistantConnectionStatus.Disconnected,
            audio = AssistantAudioStatus.Idle,
            sessionId = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = null,
            lastReconnectDecision = "assistant_disabled",
        )
    }

    override suspend fun useFakeRuntime() {
        realAudioEngine.cancel()
        realWebSocketClient.close("switch_to_fake_runtime")
        mutableState.value = stateMachine.disconnected(mutableState.value, "switch_to_fake_runtime", nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            statusText = "已切换到 Gate A Fake Runtime；真实 Xiaozhi 链路不会被使用。",
            lastReconnectDecision = "runtime_mode_fake",
        )
    }

    override suspend fun useRealRuntime() {
        realAudioEngine.cancel()
        fakeWebSocketClient.close("switch_to_real_runtime")
        realWebSocketClient.close("switch_to_real_runtime")
        realUploadedAudioFrames = 0
        mutableState.value = stateMachine.disconnected(mutableState.value, "switch_to_real_runtime", nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            statusText = "已切换到 Gate B Real Xiaozhi Runtime；请先真实 OTA，再连接真实 WebSocket。",
            lastReconnectDecision = "runtime_mode_real",
            audioUploadedFrames = 0,
        )
    }

    override suspend fun connect() {
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            connectReal()
        } else {
            connectFake()
        }
    }

    private suspend fun connectFake() {
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
            mutableState.value = stateMachine.connected(connecting, result.sessionId, nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Fake,
                fakeRuntime = true,
                deviceId = identity.deviceId,
                clientId = identity.clientId,
                websocketUrl = result.websocketUrl,
                lastClientJson = result.outgoingHelloJson,
                lastServerJson = result.incomingHelloJson,
                lastProtocolEvent = result.event.javaClass.simpleName,
                statusText = "Fake WebSocket 已完成 hello/session 握手",
            )
        } else {
            mutableState.value = stateMachine.error(mutableState.value, "Fake WebSocket hello 失败", nowMillis()).copy(
                lastClientJson = result.outgoingHelloJson,
                lastServerJson = result.incomingHelloJson,
                lastProtocolEvent = result.event.javaClass.simpleName,
                lastReconnectDecision = "connect_fake_hello_failed",
            )
        }
    }

    private suspend fun connectReal() {
        val connecting = stateMachine.connecting(mutableState.value, nowMillis())
        mutableState.value = connecting.copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            statusText = "正在连接真实 Xiaozhi WebSocket",
        )
        if (!connecting.assistantEnabled || connecting.errorMessage != null) return

        val identity = deviceIdentityManager.ensureIdentity()
        val config = XiaozhiConnectionConfig(
            websocketUrl = settingsRepository.websocketUrl.first(),
            websocketToken = settingsRepository.assistantWebsocketToken.first(),
            deviceId = identity.deviceId,
            clientId = identity.clientId,
        )
        realUploadedAudioFrames = 0
        val success = runCatching {
            realWebSocketClient.connect(config, ::handleRealWebSocketEvent)
        }.getOrElse { error ->
            mutableState.value = stateMachine.error(
                current = mutableState.value,
                message = error.message ?: error::class.java.simpleName,
                nowMillis = nowMillis(),
            ).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastReconnectDecision = "real_connect_exception",
            )
            false
        }

        if (success && realWebSocketClient.isConnected()) {
            mutableState.value = stateMachine.connected(mutableState.value, realWebSocketClient.sessionId, nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                deviceId = identity.deviceId,
                clientId = identity.clientId,
                websocketUrl = config.websocketUrl,
                gateBRealHandshakeVerified = true,
                statusText = "Gate B：真实 WebSocket hello/session 握手完成",
            )
        } else if (!mutableState.value.isConnected) {
            mutableState.value = stateMachine.error(
                current = mutableState.value.copy(runtimeMode = AssistantRuntimeMode.Real, fakeRuntime = false),
                message = "真实 WebSocket handshake 未完成；请检查真实 OTA 下发的 URL/token 与服务端状态。",
                nowMillis = nowMillis(),
            ).copy(lastReconnectDecision = "real_handshake_failed")
        }
    }

    override suspend fun reconnect() {
        realAudioEngine.cancel()
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            realWebSocketClient.close("manual_reconnect")
        } else {
            fakeWebSocketClient.close("manual_reconnect")
        }
        val nextAttempt = mutableState.value.reconnectAttempt + 1
        mutableState.value = stateMachine.reconnecting(
            current = mutableState.value,
            reason = "manual_reconnect",
            attempt = nextAttempt,
            nowMillis = nowMillis(),
        ).copy(lastReconnectDecision = "manual_reconnect_attempt_$nextAttempt")
        connect()
    }

    override suspend fun disconnect(reason: String) {
        realAudioEngine.cancel()
        val currentMode = mutableState.value.runtimeMode
        val closed = fakeWebSocketClient.close(reason)
        realWebSocketClient.close(reason)
        val current = mutableState.value
        mutableState.value = stateMachine.disconnected(current, reason, nowMillis()).copy(
            runtimeMode = currentMode,
            fakeRuntime = currentMode == AssistantRuntimeMode.Fake,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = null,
            lastCloseCode = closed.code,
            lastCloseReason = reason,
            lastReconnectDecision = "manual_disconnect",
        )
    }

    override suspend fun sendText(text: String) {
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            sendRealText(text)
        } else {
            sendFakeText(text)
        }
    }

    private suspend fun sendFakeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            mutableState.value = stateMachine.error(mutableState.value, "文本不能为空", nowMillis())
            return
        }
        if (!mutableState.value.isConnected) connectFake()
        val thinking = stateMachine.textSubmitted(mutableState.value, trimmed, nowMillis())
        mutableState.value = thinking
        if (thinking.errorMessage != null) return

        val turn = fakeWebSocketClient.sendText(trimmed)
        if (!turn.success) {
            mutableState.value = stateMachine.error(thinking, turn.message, nowMillis()).copy(lastReconnectDecision = "send_fake_text_failed")
            return
        }
        delay(80)
        val assistantText = when (val event = turn.event) {
            is ProtocolEvent.AssistantText -> event.text
            else -> turn.message
        }
        mutableState.value = stateMachine.assistantText(thinking, assistantText, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            lastClientJson = turn.outgoingJson,
            lastServerJson = turn.incomingJson,
            lastProtocolEvent = turn.event.javaClass.simpleName,
            statusText = "Fake WebSocket 文本回合完成",
        )
    }

    private suspend fun sendRealText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            mutableState.value = stateMachine.error(mutableState.value, "文本不能为空", nowMillis())
            return
        }
        if (!mutableState.value.isConnected || !realWebSocketClient.isConnected()) connectReal()
        val thinking = stateMachine.textSubmitted(mutableState.value, trimmed, nowMillis())
        mutableState.value = thinking.copy(runtimeMode = AssistantRuntimeMode.Real, fakeRuntime = false)
        if (thinking.errorMessage != null) return

        val sent = realWebSocketClient.sendText(trimmed, ::handleRealWebSocketEvent)
        if (!sent) {
            mutableState.value = stateMachine.error(thinking, "真实 WebSocket 文本发送失败", nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastReconnectDecision = "send_real_text_failed",
            )
            return
        }
        mutableState.value = thinking.copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            statusText = "Gate B：真实文本已发送，等待服务端回复",
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
                lastAudioSummary = "未获得 RECORD_AUDIO，未创建 AudioRecord / OpusEncoder",
            )
            return
        }
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            startRealPushToTalk()
        } else {
            startFakePushToTalk()
        }
    }

    private suspend fun startFakePushToTalk() {
        if (!mutableState.value.isConnected) connectFake()
        if (!mutableState.value.isConnected) return

        val listenStart = fakeWebSocketClient.sendStartListening(mode = "manual")
        if (!listenStart.success) {
            mutableState.value = stateMachine.error(mutableState.value, listenStart.message, nowMillis())
            return
        }
        val audioStart = realAudioEngine.startRecording(nowMillis()) { packet ->
            fakeWebSocketClient.sendAudioFrame(packet).success
        }
        if (!audioStart.started) {
            mutableState.value = stateMachine.error(mutableState.value.copy(audio = AssistantAudioStatus.Error), audioStart.summary, nowMillis()).copy(lastAudioSummary = audioStart.summary)
            return
        }
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            statusText = "Gate A：真实 AudioRecord / Opus 已启动，上传到 Fake WebSocket",
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

    private suspend fun startRealPushToTalk() {
        if (!mutableState.value.isConnected || !realWebSocketClient.isConnected()) connectReal()
        if (!mutableState.value.isConnected || !realWebSocketClient.isConnected()) return

        val listenStarted = realWebSocketClient.sendStartListening(mode = "manual", onEvent = ::handleRealWebSocketEvent)
        if (!listenStarted) {
            mutableState.value = stateMachine.error(mutableState.value, "真实 WebSocket listen/start 发送失败", nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
            )
            return
        }
        realUploadedAudioFrames = 0
        val audioStart = realAudioEngine.startRecording(nowMillis()) { packet ->
            val ok = realWebSocketClient.sendAudioFrame(packet)
            if (ok) realUploadedAudioFrames += 1
            ok
        }
        if (!audioStart.started) {
            mutableState.value = stateMachine.error(mutableState.value.copy(audio = AssistantAudioStatus.Error), audioStart.summary, nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastAudioSummary = audioStart.summary,
            )
            return
        }
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            statusText = "Gate B：真实 AudioRecord / Opus 已启动，正在上传到真实 Xiaozhi WebSocket",
            errorMessage = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            pushToTalkStopLatencyMs = null,
            lastAudioSummary = audioStart.summary,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun stopPushToTalk() {
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            stopRealPushToTalk()
        } else {
            stopFakePushToTalk()
        }
    }

    private suspend fun stopFakePushToTalk() {
        val stoppedAudio = realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 300L)
        val listenStop = fakeWebSocketClient.sendStopListening()
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        mutableState.value = stopped.copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = if (stoppedAudio.stoppedWithinBudget) "Gate A：真实 Audio 已在 ${stoppedAudio.stopLatencyMs}ms 内停止，开始 loopback 播放验证" else "真实 Audio 停止超时：${stoppedAudio.stopLatencyMs}ms",
            errorMessage = stoppedAudio.errorMessage ?: if (stoppedAudio.stoppedWithinBudget) null else "PTT release 后停止超过 300ms",
            lastClientJson = listenStop.outgoingJson,
            lastServerJson = listenStop.incomingJson,
            lastProtocolEvent = listenStop.event.javaClass.simpleName,
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = fakeWebSocketClient.uploadedAudioFrameCount(),
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = stoppedAudio.summary,
            gateBRealAudioUploadVerified = false,
            lastEventAt = nowMillis(),
        )
        if (!stoppedAudio.stoppedWithinBudget || stoppedAudio.errorMessage != null) {
            mutableState.value = stateMachine.error(mutableState.value.copy(audio = AssistantAudioStatus.Error), stoppedAudio.errorMessage ?: "真实音频停止失败或超时", nowMillis()).copy(lastAudioSummary = stoppedAudio.summary)
            return
        }
        delay(80)
        mutableState.value = stateMachine.speaking(mutableState.value, nowMillis()).copy(
            lastAssistantText = "Gate A：收到 ${fakeWebSocketClient.uploadedAudioFrameCount()} 帧 Opus，开始 loopback 解码播放。",
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = fakeWebSocketClient.uploadedAudioFrameCount(),
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = stoppedAudio.summary,
        )
        val playback = realAudioEngine.playOpusFrames(stoppedAudio.encodedFrames)
        mutableState.value = stateMachine.assistantText(mutableState.value, if (playback.success) "Gate A Real Audio loopback 完成：${playback.summary}" else "Gate A Real Audio loopback 未通过：${playback.summary}", nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = fakeWebSocketClient.uploadedAudioFrameCount(),
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = stoppedAudio.summary + "；" + playback.summary,
            errorMessage = if (playback.success) null else playback.summary,
        )
    }

    private suspend fun stopRealPushToTalk() {
        val stoppedAudio = realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 300L)
        val listenStopped = realWebSocketClient.sendStopListening(::handleRealWebSocketEvent)
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        mutableState.value = stopped.copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = if (stoppedAudio.stoppedWithinBudget) {
                "Gate B：真实 Audio 已上传 $realUploadedAudioFrames 帧，listen/stop=${listenStopped}，等待真实服务端文本或 TTS/audio"
            } else {
                "真实 Audio 停止超时：${stoppedAudio.stopLatencyMs}ms"
            },
            errorMessage = stoppedAudio.errorMessage ?: if (stoppedAudio.stoppedWithinBudget) null else "PTT release 后停止超过 300ms",
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = realUploadedAudioFrames,
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = stoppedAudio.summary,
            gateBRealAudioUploadVerified = realUploadedAudioFrames > 0,
            lastEventAt = nowMillis(),
        )
        if (!stoppedAudio.stoppedWithinBudget || stoppedAudio.errorMessage != null) {
            mutableState.value = stateMachine.error(mutableState.value.copy(audio = AssistantAudioStatus.Error), stoppedAudio.errorMessage ?: "真实音频停止失败或超时", nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastAudioSummary = stoppedAudio.summary,
            )
        }
    }

    override suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String) {
        if (!fakeWebSocketClient.isConnected()) connectFake()
        val turn = fakeWebSocketClient.simulateIncomingToolCall(toolName, argumentsJson)
        mutableState.value = mutableState.value.copy(
            lastAssistantText = turn.message,
            statusText = if (turn.success) "MCP tools/call 已安全处理：$toolName" else turn.message,
            lastServerJson = turn.incomingJson,
            lastClientJson = turn.outgoingResponseJson,
            lastProtocolEvent = turn.event.javaClass.simpleName,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun simulateIncomingToolsList() {
        if (!fakeWebSocketClient.isConnected()) connectFake()
        val turn = fakeWebSocketClient.simulateIncomingToolsListRequest()
        mutableState.value = mutableState.value.copy(
            lastAssistantText = turn.message,
            statusText = if (turn.success) "Phase4 MCP tools/list 已通过 runtime executor 返回 descriptors" else turn.message,
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
        realWebSocketClient.close("identity_reset")
        realUploadedAudioFrames = 0
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
            lastReconnectDecision = "identity_reset",
            gateBRealHandshakeVerified = false,
            gateBRealTextVerified = false,
            gateBRealAudioUploadVerified = false,
            gateBRealAudioPlaybackVerified = false,
            gateBRealToolCallBlockedVerified = false,
            statusText = "设备身份已重置：${identity.displayDeviceId}，需要重新激活。",
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun runFakeActivation() {
        if (!mutableState.value.assistantEnabled) enableAssistant()
        mutableState.value = mutableState.value.copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
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
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
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

    override suspend fun simulateConnectionClosed(code: Int, reason: String) {
        realAudioEngine.cancel()
        val closed = fakeWebSocketClient.simulateServerClose(code, reason)
        handleTransportClosed(closed)
    }

    override suspend fun simulateConnectionFailure(message: String) {
        realAudioEngine.cancel()
        val failure = fakeWebSocketClient.simulateTransportFailure(message)
        val current = mutableState.value
        val decision = reconnectPolicy.decideFailure(
            assistantEnabled = current.assistantEnabled,
            currentAttempt = current.reconnectAttempt,
        )
        mutableState.value = stateMachine.error(
            current = current.copy(connection = AssistantConnectionStatus.Disconnected, sessionId = null, audio = AssistantAudioStatus.Idle),
            message = failure.message,
            nowMillis = nowMillis(),
        ).copy(
            lastProtocolEvent = failure.javaClass.simpleName,
            lastReconnectDecision = decision.decisionLabel,
            lastCloseReason = failure.message,
        )
    }

    override suspend fun simulateAudioFailure(message: String) {
        realAudioEngine.cancel()
        mutableState.value = stateMachine.error(
            current = mutableState.value.copy(audio = AssistantAudioStatus.Error),
            message = message,
            nowMillis = nowMillis(),
        ).copy(
            statusText = "音频链路失败：$message",
            lastAudioSummary = "已停止录音/播放并释放真实音频资源：$message",
            pushToTalkStopLatencyMs = null,
        )
    }

    private fun handleRealWebSocketEvent(event: XiaozhiWebSocketEvent) {
        when (event) {
            is XiaozhiWebSocketEvent.Log -> {
                mutableState.value = mutableState.value.copy(statusText = event.message, lastProtocolEvent = event.javaClass.simpleName, lastEventAt = nowMillis())
            }
            is XiaozhiWebSocketEvent.OutgoingText -> {
                mutableState.value = mutableState.value.copy(lastClientJson = event.json, lastProtocolEvent = event.javaClass.simpleName, lastEventAt = nowMillis())
            }
            is XiaozhiWebSocketEvent.IncomingText -> {
                handleRealProtocolEvent(rawJson = event.json, event = event.event)
            }
            is XiaozhiWebSocketEvent.IncomingBinary -> {
                handleRealIncomingAudio(event.bytes)
            }
            is XiaozhiWebSocketEvent.Connected -> {
                mutableState.value = stateMachine.connected(mutableState.value, event.sessionId, nowMillis()).copy(
                    runtimeMode = AssistantRuntimeMode.Real,
                    fakeRuntime = false,
                    gateBRealHandshakeVerified = true,
                    statusText = "Gate B：真实 WebSocket hello/session 握手完成",
                    lastProtocolEvent = event.javaClass.simpleName,
                )
            }
            is XiaozhiWebSocketEvent.Closed -> {
                scope.launch { handleTransportClosed(event) }
            }
            is XiaozhiWebSocketEvent.Error -> {
                mutableState.value = stateMachine.error(
                    current = mutableState.value.copy(connection = AssistantConnectionStatus.Disconnected, sessionId = null),
                    message = event.message,
                    nowMillis = nowMillis(),
                ).copy(
                    runtimeMode = AssistantRuntimeMode.Real,
                    fakeRuntime = false,
                    lastProtocolEvent = event.javaClass.simpleName,
                    lastReconnectDecision = "real_websocket_error",
                )
            }
        }
    }

    private fun handleRealProtocolEvent(rawJson: String, event: ProtocolEvent) {
        val base = mutableState.value.copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            lastServerJson = rawJson,
            lastProtocolEvent = event.javaClass.simpleName,
            lastEventAt = nowMillis(),
        )
        when (event) {
            is ProtocolEvent.Connected -> {
                mutableState.value = stateMachine.connected(base, event.sessionId, nowMillis()).copy(
                    runtimeMode = AssistantRuntimeMode.Real,
                    fakeRuntime = false,
                    gateBRealHandshakeVerified = event.sessionId.isNotBlank(),
                    statusText = "Gate B：真实服务端 hello 已解析，session=${event.sessionId}",
                )
            }
            is ProtocolEvent.AssistantText -> {
                mutableState.value = stateMachine.assistantText(base, event.text, nowMillis()).copy(
                    runtimeMode = AssistantRuntimeMode.Real,
                    fakeRuntime = false,
                    gateBRealTextVerified = true,
                    statusText = "Gate B：收到真实助手文本回复",
                )
            }
            is ProtocolEvent.TtsState -> {
                val next = if (event.state == "stop" || event.state == "end") {
                    realAudioEngine.stopPlaybackAndRelease()
                    base.copy(phase = AssistantPhase.Connected, audio = AssistantAudioStatus.Idle)
                } else {
                    base.copy(phase = AssistantPhase.Speaking, audio = AssistantAudioStatus.Playing)
                }
                mutableState.value = next.copy(statusText = "Gate B：真实 TTS state=${event.state}")
            }
            is ProtocolEvent.ListenState -> {
                mutableState.value = base.copy(statusText = "Gate B：真实 listen state=${event.state}")
            }
            is ProtocolEvent.McpResponse -> {
                val sent = realWebSocketClient.sendMcpResponse(event.responseJson, ::handleRealWebSocketEvent)
                mutableState.value = base.copy(
                    lastAssistantText = event.message,
                    statusText = "Gate B：真实服务端 MCP ${event.requestMethod ?: "request"} 已在 Phase3 边界处理，response_sent=$sent",
                    gateBRealToolCallBlockedVerified = event.blocked && sent,
                )
            }
            is ProtocolEvent.ProtocolError -> {
                mutableState.value = base.copy(
                    statusText = "真实协议消息解析失败：${event.reason}",
                    errorMessage = "protocol_error=${event.reason}",
                )
            }
            is ProtocolEvent.RawJson -> {
                mutableState.value = base.copy(statusText = "Gate B：收到真实未处理消息 type=${event.type}")
            }
            is ProtocolEvent.ToolCall -> {
                mutableState.value = base.copy(statusText = "Gate B：收到旧式 ToolCall 事件 ${event.toolName}，未执行便签工具")
            }
            is ProtocolEvent.BinaryAudio -> handleRealIncomingAudio(event.data)
            is ProtocolEvent.Closed -> {
                mutableState.value = stateMachine.disconnected(base, event.reason, nowMillis())
            }
            is ProtocolEvent.Error -> {
                mutableState.value = stateMachine.error(base, event.message, nowMillis())
            }
        }
    }

    private fun handleRealIncomingAudio(bytes: ByteArray) {
        scope.launch {
            val before = stateMachine.speaking(mutableState.value, nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastProtocolEvent = "IncomingBinary",
                statusText = "Gate B：收到真实服务端二进制音频，正在解码播放",
            )
            mutableState.value = before
            val playback = realAudioEngine.playIncomingOpusFrame(bytes)
            mutableState.value = mutableState.value.copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                audio = if (playback.success) AssistantAudioStatus.Playing else mutableState.value.audio,
                lastAudioSummary = playback.summary,
                lastAssistantText = playback.summary,
                gateBRealAudioPlaybackVerified = mutableState.value.gateBRealAudioPlaybackVerified || playback.success,
                statusText = if (playback.success) "Gate B：真实服务端 audio/TTS 已解码并写入 AudioTrack" else "Gate B：真实服务端音频帧已收到，等待可播放 PCM",
                lastEventAt = nowMillis(),
            )
        }
    }

    private suspend fun handleTransportClosed(closed: XiaozhiWebSocketEvent.Closed) {
        realAudioEngine.cancel()
        val current = mutableState.value
        val decision = reconnectPolicy.decideClose(
            closeCode = closed.code,
            reason = closed.reason,
            assistantEnabled = current.assistantEnabled,
            currentAttempt = current.reconnectAttempt,
        )
        if (!decision.shouldReconnect) {
            mutableState.value = stateMachine.disconnected(current, closed.reason, nowMillis()).copy(
                runtimeMode = current.runtimeMode,
                fakeRuntime = current.runtimeMode == AssistantRuntimeMode.Fake,
                lastCloseCode = closed.code,
                lastCloseReason = closed.reason,
                lastReconnectDecision = decision.decisionLabel,
                statusText = decision.userMessage,
            )
            return
        }
        mutableState.value = stateMachine.reconnecting(current, closed.reason, decision.nextAttempt, nowMillis()).copy(
            runtimeMode = current.runtimeMode,
            fakeRuntime = current.runtimeMode == AssistantRuntimeMode.Fake,
            lastCloseCode = closed.code,
            lastCloseReason = closed.reason,
            lastReconnectDecision = decision.decisionLabel,
            statusText = decision.userMessage,
        )
        delay(120)
        connect()
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
