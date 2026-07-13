package com.er1cmo.noteassistant.assistant.runtime.controller

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.app.settings.VoiceConversationSettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.audio.VoiceAutomaticStopReason
import com.er1cmo.noteassistant.assistant.runtime.audio.VoiceCaptureConfig
import com.er1cmo.noteassistant.assistant.runtime.audio.VoiceActivitySnapshot
import com.er1cmo.noteassistant.core.common.audio.MicrophoneLease
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwner
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwnershipCoordinator
import com.er1cmo.noteassistant.core.common.audio.WakeWordAudioGate
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationClient
import com.er1cmo.noteassistant.assistant.runtime.activation.OtaActivationState
import com.er1cmo.noteassistant.assistant.runtime.audio.RealAudioEngine
import com.er1cmo.noteassistant.assistant.runtime.conversation.ConversationStateMachine
import com.er1cmo.noteassistant.assistant.runtime.context.AssistantTurnContextStore
import com.er1cmo.noteassistant.assistant.runtime.identity.DeviceIdentityManager
import com.er1cmo.noteassistant.assistant.runtime.network.FakeXiaozhiWebSocketClient
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiConnectionConfig
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiWebSocketClient
import com.er1cmo.noteassistant.assistant.runtime.network.XiaozhiWebSocketEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.recovery.ReconnectPolicy
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantRuntimeMode
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.StreamingConversationState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceActivityState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@Singleton
class LocalAssistantController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val stateMachine: ConversationStateMachine,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val otaActivationClient: OtaActivationClient,
    private val fakeWebSocketClient: FakeXiaozhiWebSocketClient,
    private val realWebSocketClient: XiaozhiWebSocketClient,
    private val realAudioEngine: RealAudioEngine,
    private val voiceConversationSettingsRepository: VoiceConversationSettingsRepository,
    private val microphoneOwnershipCoordinator: MicrophoneOwnershipCoordinator,
    private val wakeWordAudioGate: WakeWordAudioGate,
    private val assistantTurnContextStore: AssistantTurnContextStore,
    private val reconnectPolicy: ReconnectPolicy,
) : AssistantController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AssistantState.disabled(nowMillis()))

    @Volatile
    private var realUploadedAudioFrames: Int = 0

    private var assistantMicrophoneLease: MicrophoneLease? = null
    private var pttGeneration: Long = 0L
    private var streamingGeneration: Long = 0L
    private var streamingTurnToken: Long = 0L
    private var streamingFinalizedTurnToken: Long = -1L
    private var streamingNextTurnJob: Job? = null
    private var streamingResponseWatchdogJob: Job? = null
    private var pttWakeWordResumeJob: Job? = null
    private var responsePlaybackIdleJob: Job? = null
    private var automaticReconnectJob: Job? = null
    @Volatile private var manualDisconnectRequested: Boolean = false
    private var pttResponseExpected: Boolean = false
    @Volatile private var bargeInTriggered: Boolean = false
    @Volatile private var bargeInStarting: Boolean = false
    private var bargeInMonitorToken: Long = -1L
    private var bargeInMonitorGeneration: Long = 0L

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
        scope.launch {
            voiceConversationSettingsRepository.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(
                    preferredVoiceMode = VoiceInteractionMode.fromStorage(settings.defaultMode),
                    streamingIdleTimeoutMs = settings.streamingIdleTimeoutMs,
                    streamingBargeInEnabled = settings.streamingBargeInEnabled,
                )
            }
        }
        scope.launch {
            microphoneOwnershipCoordinator.state.collect { ownership ->
                mutableState.value = mutableState.value.copy(microphoneOwner = ownership.owner)
            }
        }
        scope.launch {
            val enabled = settingsRepository.assistantEnabled.first()
            val activated = settingsRepository.assistantActivationStatus.first()
            if (enabled && activated) {
                delay(450L)
                mutableState.value = mutableState.value.copy(
                    assistantEnabled = true,
                    runtimeMode = AssistantRuntimeMode.Real,
                    fakeRuntime = false,
                    activation = AssistantActivationStatus.Activated,
                    errorMessage = null,
                    statusText = "正在恢复语音服务",
                )
                scheduleAutomaticReconnect("app_start")
            }
        }
    }

    override suspend fun enableAssistant() {
        settingsRepository.setAssistantEnabled(true)
        mutableState.value = stateMachine.enable(mutableState.value, nowMillis())
    }

    override suspend fun disableAssistant() {
        manualDisconnectRequested = true
        automaticReconnectJob?.cancel()
        automaticReconnectJob = null
        responsePlaybackIdleJob?.cancel()
        pttResponseExpected = false
        stopStreamingConversationInternal("assistant_disabled", resumeWakeWord = false)
        releaseAssistantMicrophone("assistant_disabled", resumeWakeWord = false)
        settingsRepository.setAssistantEnabled(false)
        assistantTurnContextStore.clear()
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
            statusText = "已切换到 Fake Runtime；MCP 工具调用将按本地模拟来源记录。",
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
            statusText = "已切换到 Real Xiaozhi Runtime；请先真实 OTA，再连接真实 WebSocket。",
            lastReconnectDecision = "runtime_mode_real",
            audioUploadedFrames = 0,
        )
    }

    override suspend fun connect() {
        manualDisconnectRequested = false
        automaticReconnectJob?.cancel()
        automaticReconnectJob = null
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
            websocketToken = settingsRepository.assistantWebsocketToken.first().ifBlank { "phase4-fake-token" },
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
            mutableState.value = stateMachine.disconnected(
                current = mutableState.value,
                reason = error.message ?: error::class.java.simpleName,
                nowMillis = nowMillis(),
            ).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                errorMessage = null,
                statusText = "暂时无法连接语音服务",
                lastCloseReason = error.message ?: error::class.java.simpleName,
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
                reconnectAttempt = 0,
                errorMessage = null,
                statusText = "语音服务已连接",
            )
            automaticReconnectJob?.cancel()
            automaticReconnectJob = null
        } else if (!mutableState.value.isConnected) {
            mutableState.value = stateMachine.disconnected(
                current = mutableState.value.copy(runtimeMode = AssistantRuntimeMode.Real, fakeRuntime = false),
                reason = "real_handshake_failed",
                nowMillis = nowMillis(),
            ).copy(
                errorMessage = null,
                statusText = "暂时无法连接语音服务，点击小智可重试",
                lastReconnectDecision = "real_handshake_failed",
            )
        }
    }

    override suspend fun reconnect() {
        manualDisconnectRequested = false
        automaticReconnectJob?.cancel()
        automaticReconnectJob = null
        responsePlaybackIdleJob?.cancel()
        pttResponseExpected = false
        stopStreamingConversationInternal("manual_reconnect", resumeWakeWord = false)
        releaseAssistantMicrophone("manual_reconnect", resumeWakeWord = false)
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
        manualDisconnectRequested = true
        automaticReconnectJob?.cancel()
        automaticReconnectJob = null
        responsePlaybackIdleJob?.cancel()
        pttResponseExpected = false
        stopStreamingConversationInternal(reason, resumeWakeWord = true)
        releaseAssistantMicrophone(reason, resumeWakeWord = false)
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
            statusText = "真实文本已发送，等待服务端回复",
        )
    }

    override suspend fun startPushToTalk(hasRecordAudioPermission: Boolean) {
        pttGeneration += 1L
        val generation = pttGeneration
        pttResponseExpected = false
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
        if (mutableState.value.streamingSessionActive) {
            stopStreamingConversationInternal("switch_to_push_to_talk", resumeWakeWord = false)
        }
        if (!prepareAssistantCapture(AssistantEntrySource.PushToTalk, "push_to_talk")) return
        assistantTurnContextStore.beginSession(
            entrySource = AssistantEntrySource.PushToTalk,
            conversationId = "ptt-${UUID.randomUUID()}",
        )
        if (pttGeneration != generation) {
            releaseAssistantMicrophone("push_to_talk_released_before_start", resumeWakeWord = true)
            return
        }
        if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
            startRealPushToTalk()
        } else {
            startFakePushToTalk()
        }
        if (pttGeneration != generation && realAudioEngine.isRecording()) {
            if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
                stopRealPushToTalk()
            } else {
                stopFakePushToTalk()
            }
        }
        if (!realAudioEngine.isRecording()) {
            assistantTurnContextStore.clear()
            releaseAssistantMicrophone("push_to_talk_start_failed_or_released", resumeWakeWord = true)
        } else {
            mutableState.value = mutableState.value.copy(
                activeEntrySource = AssistantEntrySource.PushToTalk,
                microphoneOwner = MicrophoneOwner.AssistantCapture,
            )
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
        val audioStart = realAudioEngine.startRecording(
            nowMillis = nowMillis(),
            onEncodedFrame = { packet ->
                fakeWebSocketClient.sendAudioFrame(packet).success
            },
        )
        if (!audioStart.started) {
            mutableState.value = stateMachine.error(mutableState.value.copy(audio = AssistantAudioStatus.Error), audioStart.summary, nowMillis()).copy(lastAudioSummary = audioStart.summary)
            return
        }
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            statusText = "真实 AudioRecord / Opus 已启动，上传到 Fake WebSocket",
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
        val audioStart = realAudioEngine.startRecording(
            nowMillis = nowMillis(),
            onEncodedFrame = { packet ->
                val ok = realWebSocketClient.sendAudioFrame(packet)
                if (ok) realUploadedAudioFrames += 1
                ok
            },
        )
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
            statusText = "真实 AudioRecord / Opus 已启动，正在上传到真实 Xiaozhi WebSocket",
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
        pttGeneration += 1L
        try {
            if (realAudioEngine.isRecording()) {
                if (mutableState.value.runtimeMode == AssistantRuntimeMode.Real) {
                    stopRealPushToTalk()
                } else {
                    stopFakePushToTalk()
                }
            }
        } finally {
            releaseAssistantMicrophone("push_to_talk_released", resumeWakeWord = false)
            if (pttResponseExpected) {
                schedulePushToTalkWakeWordResume(RESPONSE_FALLBACK_RESUME_MS)
            } else {
                assistantTurnContextStore.clear()
                mutableState.value = mutableState.value.copy(
                    phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                    audio = AssistantAudioStatus.Idle,
                    activeEntrySource = null,
                    statusText = "未检测到有效语音，本轮已取消",
                    lastEventAt = nowMillis(),
                )
                wakeWordAudioGate.resumeAfterAssistant("按住说话未检测到有效语音")
            }
        }
    }

    private suspend fun stopFakePushToTalk() {
        val stoppedAudio = realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 300L)
        if (!stoppedAudio.speechSeen) {
            fakeWebSocketClient.sendStopListening()
            pttResponseExpected = false
            assistantTurnContextStore.clear()
            mutableState.value = mutableState.value.copy(
                runtimeMode = AssistantRuntimeMode.Fake,
                fakeRuntime = true,
                phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                audio = AssistantAudioStatus.Idle,
                activeEntrySource = null,
                statusText = "Fake PTT 未检测到有效语音，本轮已取消",
                audioCapturedFrames = stoppedAudio.pcmFrames,
                audioEncodedFrames = stoppedAudio.opusFrames,
                audioUploadedFrames = fakeWebSocketClient.uploadedAudioFrameCount(),
                lastAudioSummary = stoppedAudio.summary,
                lastEventAt = nowMillis(),
            )
            return
        }
        pttResponseExpected = true
        val listenStop = fakeWebSocketClient.sendStopListening()
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        mutableState.value = stopped.copy(
            runtimeMode = AssistantRuntimeMode.Fake,
            fakeRuntime = true,
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = if (stoppedAudio.stoppedWithinBudget) "真实 Audio 已在 ${stoppedAudio.stopLatencyMs}ms 内停止，开始 loopback 播放验证" else "真实 Audio 停止超时：${stoppedAudio.stopLatencyMs}ms",
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
            lastAssistantText = "收到 ${fakeWebSocketClient.uploadedAudioFrameCount()} 帧 Opus，开始 loopback 解码播放。",
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = fakeWebSocketClient.uploadedAudioFrameCount(),
            pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
            lastAudioSummary = stoppedAudio.summary,
        )
        val playback = realAudioEngine.playOpusFrames(stoppedAudio.encodedFrames)
        mutableState.value = stateMachine.assistantText(mutableState.value, if (playback.success) "Real Audio loopback 完成：${playback.summary}" else "Real Audio loopback 未通过：${playback.summary}", nowMillis()).copy(
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
        if (!stoppedAudio.speechSeen) {
            val aborted = realWebSocketClient.sendAbort(
                reason = "ptt_no_speech",
                onEvent = ::handleRealWebSocketEvent,
            )
            pttResponseExpected = false
            assistantTurnContextStore.clear()
            mutableState.value = mutableState.value.copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                audio = AssistantAudioStatus.Idle,
                activeEntrySource = null,
                statusText = "未检测到有效语音，已取消本轮，abort=$aborted",
                audioCapturedFrames = stoppedAudio.pcmFrames,
                audioEncodedFrames = stoppedAudio.opusFrames,
                audioUploadedFrames = realUploadedAudioFrames,
                pushToTalkStopLatencyMs = stoppedAudio.stopLatencyMs,
                lastAudioSummary = stoppedAudio.summary,
                lastEventAt = nowMillis(),
            )
            return
        }
        val listenStopped = realWebSocketClient.sendStopListening(::handleRealWebSocketEvent)
        pttResponseExpected = listenStopped
        val stopped = stateMachine.stopListening(mutableState.value, nowMillis())
        mutableState.value = stopped.copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            phase = AssistantPhase.Thinking,
            audio = AssistantAudioStatus.Idle,
            statusText = if (stoppedAudio.stoppedWithinBudget) {
                "真实 Audio 已上传 $realUploadedAudioFrames 帧，listen/stop=$listenStopped，等待真实服务端文本或 TTS/audio"
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

    override suspend fun setVoiceInteractionMode(mode: VoiceInteractionMode) {
        if (mutableState.value.streamingSessionActive) {
            stopStreamingConversationInternal("voice_mode_changed", resumeWakeWord = true)
        }
        voiceConversationSettingsRepository.setDefaultMode(mode.storageValue)
        mutableState.value = mutableState.value.copy(
            preferredVoiceMode = mode,
            statusText = "默认语音模式已切换为：${mode.label}",
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun setStreamingBargeInEnabled(enabled: Boolean) {
        voiceConversationSettingsRepository.setStreamingBargeInEnabled(enabled)
        if (!enabled) {
            stopBargeInMonitor("barge_in_disabled")
        }
        mutableState.value = mutableState.value.copy(
            streamingBargeInEnabled = enabled,
            statusText = if (enabled) "流式 TTS 插话已开启" else "流式 TTS 插话已关闭",
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun startStreamingConversation(
        hasRecordAudioPermission: Boolean,
        source: AssistantEntrySource,
        wakeKeyword: String?,
    ) {
        if (!hasRecordAudioPermission) {
            mutableState.value = stateMachine.error(
                mutableState.value.copy(audio = AssistantAudioStatus.Error),
                "缺少 RECORD_AUDIO 权限，无法开始流式对话",
                nowMillis(),
            )
            return
        }
        if (mutableState.value.streamingSessionActive) return
        if (!mutableState.value.assistantEnabled) enableAssistant()
        if (mutableState.value.runtimeMode != AssistantRuntimeMode.Real) useRealRuntime()

        pttWakeWordResumeJob?.cancel()
        streamingGeneration += 1L
        val generation = streamingGeneration
        val conversationSessionId = UUID.randomUUID().toString()
        assistantTurnContextStore.beginSession(
            entrySource = source,
            conversationId = conversationSessionId,
            wakeKeyword = wakeKeyword,
        )
        mutableState.value = mutableState.value.copy(
            preferredVoiceMode = VoiceInteractionMode.StreamingConversation,
            activeEntrySource = source,
            streamingConversationState = StreamingConversationState.Starting,
            streamingSessionActive = true,
            streamingSessionId = conversationSessionId,
            streamingTurnIndex = 0,
            bargeInMonitorActive = false,
            vadState = VoiceActivityState.Warmup,
            vadStatusText = "流式对话正在准备第一轮",
            statusText = if (wakeKeyword.isNullOrBlank()) {
                "流式对话启动中"
            } else {
                "唤醒词 $wakeKeyword 已进入流式对话准备"
            },
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
        startStreamingTurn(generation)
    }

    override suspend fun stopStreamingConversation(reason: String) {
        stopStreamingConversationInternal(reason, resumeWakeWord = true)
    }

    private suspend fun startStreamingTurn(generation: Long) {
        if (!isStreamingGenerationActive(generation)) return
        if (bargeInMonitorToken >= 0L) stopBargeInMonitor("normal_streaming_turn_start")
        if (!mutableState.value.isConnected || !realWebSocketClient.isConnected()) connectReal()
        if (!isStreamingGenerationActive(generation)) return
        if (!mutableState.value.isConnected || !realWebSocketClient.isConnected()) {
            mutableState.value = mutableState.value.copy(
                streamingConversationState = StreamingConversationState.Error,
                statusText = "流式对话无法建立真实 WebSocket",
                errorMessage = "streaming_connect_failed",
            )
            stopStreamingConversationInternal("streaming_connect_failed", resumeWakeWord = true)
            return
        }

        if (!prepareAssistantCapture(mutableState.value.activeEntrySource ?: AssistantEntrySource.StreamingButton, "streaming_turn")) {
            mutableState.value = mutableState.value.copy(
                streamingConversationState = StreamingConversationState.Error,
                statusText = "流式对话未取得麦克风",
            )
            stopStreamingConversationInternal("streaming_microphone_failed", resumeWakeWord = true)
            return
        }

        val listenStarted = realWebSocketClient.sendStartListening(mode = "manual", onEvent = ::handleRealWebSocketEvent)
        if (!listenStarted) {
            releaseAssistantMicrophone("streaming_listen_start_failed", resumeWakeWord = false)
            mutableState.value = mutableState.value.copy(
                streamingConversationState = StreamingConversationState.Error,
                statusText = "流式对话 listen/start 发送失败",
            )
            stopStreamingConversationInternal("streaming_listen_start_failed", resumeWakeWord = true)
            return
        }

        realUploadedAudioFrames = 0
        streamingTurnToken += 1L
        val turnToken = streamingTurnToken
        val audioStart = realAudioEngine.startRecording(
            nowMillis = nowMillis(),
            onEncodedFrame = { packet ->
                val ok = realWebSocketClient.sendAudioFrame(packet)
                if (ok) realUploadedAudioFrames += 1
                ok
            },
            captureConfig = VoiceCaptureConfig.streaming(mutableState.value.streamingIdleTimeoutMs),
            onVoiceActivity = { snapshot -> onStreamingVoiceActivity(snapshot, generation, turnToken) },
            onAutomaticStop = { reason ->
                scope.launch { handleStreamingAutomaticStop(reason, generation, turnToken) }
            },
        )
        if (!audioStart.started) {
            releaseAssistantMicrophone("streaming_audio_start_failed", resumeWakeWord = false)
            mutableState.value = stateMachine.error(
                mutableState.value.copy(
                    streamingConversationState = StreamingConversationState.Error,
                    audio = AssistantAudioStatus.Error,
                ),
                audioStart.summary,
                nowMillis(),
            )
            stopStreamingConversationInternal("streaming_audio_start_failed", resumeWakeWord = true)
            return
        }

        val nextTurn = mutableState.value.streamingTurnIndex + 1
        mutableState.value = stateMachine.startListening(mutableState.value, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            activeEntrySource = mutableState.value.activeEntrySource ?: AssistantEntrySource.StreamingButton,
            microphoneOwner = MicrophoneOwner.AssistantCapture,
            streamingConversationState = StreamingConversationState.ListeningForSpeech,
            streamingSessionActive = true,
            streamingTurnIndex = nextTurn,
            vadState = VoiceActivityState.Warmup,
            vadStatusText = "第 $nextTurn 轮：VAD 预热中",
            statusText = "流式对话第 $nextTurn 轮正在聆听",
            errorMessage = null,
            audioCapturedFrames = 0,
            audioEncodedFrames = 0,
            audioUploadedFrames = 0,
            lastAudioSummary = audioStart.summary,
            lastEventAt = nowMillis(),
        )
    }

    private fun onStreamingVoiceActivity(
        snapshot: VoiceActivitySnapshot,
        generation: Long,
        turnToken: Long,
    ) {
        if (!isStreamingGenerationActive(generation) || streamingTurnToken != turnToken) return
        val streamingState = when (snapshot.state) {
            VoiceActivityState.SpeechDetected,
            VoiceActivityState.SpeechActive -> StreamingConversationState.UserSpeaking
            VoiceActivityState.EndOfSpeech -> StreamingConversationState.SubmittingTurn
            VoiceActivityState.NoSpeechTimeout -> StreamingConversationState.Stopping
            else -> StreamingConversationState.ListeningForSpeech
        }
        mutableState.value = mutableState.value.copy(
            streamingConversationState = streamingState,
            vadState = snapshot.state,
            vadStatusText = "${snapshot.state.storageValue} · peak=${snapshot.peakAbs} rms=${snapshot.rms} · ${snapshot.elapsedMs}ms",
            statusText = when (snapshot.state) {
                VoiceActivityState.SpeechDetected -> "流式对话检测到用户起声"
                VoiceActivityState.SpeechActive -> "流式对话正在接收用户语音"
                VoiceActivityState.EndOfSpeech -> "流式对话检测到停顿，准备提交本轮"
                VoiceActivityState.NoSpeechTimeout -> "流式对话等待语音超时"
                else -> mutableState.value.statusText
            },
            lastEventAt = nowMillis(),
        )
    }

    private suspend fun handleStreamingAutomaticStop(
        reason: VoiceAutomaticStopReason,
        generation: Long,
        turnToken: Long,
    ) {
        if (!isStreamingGenerationActive(generation) || streamingTurnToken != turnToken) return
        if (streamingFinalizedTurnToken == turnToken) return
        streamingFinalizedTurnToken = turnToken
        val finalizedBargeIn = bargeInMonitorToken == turnToken
        if (finalizedBargeIn) {
            bargeInMonitorGeneration += 1L
            bargeInMonitorToken = -1L
            bargeInTriggered = false
            bargeInStarting = false
        }
        val stoppedAudio = realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 1_500L)
        releaseAssistantMicrophone("streaming_turn_audio_released", resumeWakeWord = false)

        if (reason == VoiceAutomaticStopReason.NoSpeechTimeout) {
            val aborted = realWebSocketClient.sendAbort(
                reason = "streaming_no_speech_timeout",
                onEvent = ::handleRealWebSocketEvent,
            )
            mutableState.value = mutableState.value.copy(
                phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                audio = AssistantAudioStatus.Idle,
                streamingConversationState = StreamingConversationState.Stopping,
                vadState = VoiceActivityState.NoSpeechTimeout,
                vadStatusText = "等待用户语音超过 ${mutableState.value.streamingIdleTimeoutMs}ms",
                statusText = "未检测到语音，流式对话已取消，abort=$aborted",
                lastAudioSummary = stoppedAudio.summary,
            )
            stopStreamingConversationInternal(
                reason = "streaming_idle_timeout",
                resumeWakeWord = true,
                finalizeTransport = false,
            )
            return
        }

        val hasUsefulAudio = stoppedAudio.speechSeen &&
            stoppedAudio.opusFrames >= MIN_STREAMING_OPUS_PACKETS
        val transportSent = if (hasUsefulAudio) {
            realWebSocketClient.sendStopListening(::handleRealWebSocketEvent)
        } else {
            realWebSocketClient.sendAbort(
                reason = "streaming_empty_turn",
                onEvent = ::handleRealWebSocketEvent,
            )
        }
        mutableState.value = stateMachine.stopListening(mutableState.value, nowMillis()).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            phase = if (hasUsefulAudio) AssistantPhase.Thinking else AssistantPhase.Connected,
            audio = AssistantAudioStatus.Idle,
            microphoneOwner = MicrophoneOwner.None,
            bargeInMonitorActive = false,
            streamingConversationState = if (hasUsefulAudio) {
                StreamingConversationState.Thinking
            } else {
                StreamingConversationState.WaitingForNextTurn
            },
            vadState = VoiceActivityState.EndOfSpeech,
            vadStatusText = "VAD 已提交第 ${mutableState.value.streamingTurnIndex} 轮",
            statusText = if (hasUsefulAudio) {
                "流式对话已自动提交，transport_sent=$transportSent，等待回复"
            } else {
                "本轮有效语音不足，准备重新聆听"
            },
            audioCapturedFrames = stoppedAudio.pcmFrames,
            audioEncodedFrames = stoppedAudio.opusFrames,
            audioUploadedFrames = realUploadedAudioFrames,
            lastAudioSummary = stoppedAudio.summary,
            gateBRealAudioUploadVerified = mutableState.value.gateBRealAudioUploadVerified || realUploadedAudioFrames > 0,
            lastEventAt = nowMillis(),
        )
        if (hasUsefulAudio) {
            scheduleStreamingResponseWatchdog(generation)
        } else {
            scheduleNextStreamingTurn(generation, STREAMING_EMPTY_TURN_RETRY_MS)
        }
    }

    private fun scheduleNextStreamingTurn(generation: Long, delayMs: Long) {
        streamingResponseWatchdogJob?.cancel()
        streamingNextTurnJob?.cancel()
        if (!isStreamingGenerationActive(generation)) return
        mutableState.value = mutableState.value.copy(
            streamingConversationState = StreamingConversationState.WaitingForNextTurn,
            vadStatusText = "等待下一轮聆听",
        )
        streamingNextTurnJob = scope.launch {
            delay(delayMs)
            if (isStreamingGenerationActive(generation) && !realAudioEngine.isRecording()) {
                startStreamingTurn(generation)
            }
        }
    }

    private fun scheduleStreamingResponseWatchdog(generation: Long) {
        streamingResponseWatchdogJob?.cancel()
        streamingResponseWatchdogJob = scope.launch {
            delay(STREAMING_RESPONSE_TIMEOUT_MS)
            if (!isStreamingGenerationActive(generation)) return@launch
            mutableState.value = mutableState.value.copy(
                statusText = "流式对话等待回复超时，恢复下一轮聆听",
                streamingConversationState = StreamingConversationState.Recovering,
            )
            scheduleNextStreamingTurn(generation, STREAMING_RECOVERY_DELAY_MS)
        }
    }

    private suspend fun stopStreamingConversationInternal(
        reason: String,
        resumeWakeWord: Boolean,
        finalizeTransport: Boolean = true,
    ) {
        val wasActive = mutableState.value.streamingSessionActive
        streamingFinalizedTurnToken = streamingTurnToken
        streamingGeneration += 1L
        streamingNextTurnJob?.cancel()
        streamingNextTurnJob = null
        streamingResponseWatchdogJob?.cancel()
        streamingResponseWatchdogJob = null
        responsePlaybackIdleJob?.cancel()
        responsePlaybackIdleJob = null
        bargeInMonitorGeneration += 1L
        bargeInMonitorToken = -1L
        bargeInTriggered = false
        bargeInStarting = false
        if (wasActive) {
            mutableState.value = mutableState.value.copy(
                streamingConversationState = StreamingConversationState.Stopping,
                statusText = "正在结束流式对话：$reason",
            )
        }
        val wasRecording = realAudioEngine.isRecording()
        val stoppedAudio = if (wasRecording) {
            realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 1_500L)
        } else {
            null
        }
        if (wasActive && realWebSocketClient.isConnected() && finalizeTransport) {
            if (stoppedAudio?.speechSeen == true) {
                realWebSocketClient.sendStopListening(::handleRealWebSocketEvent)
            } else {
                realWebSocketClient.sendAbort(
                    reason = "streaming_cancel:${reason.take(48)}",
                    onEvent = ::handleRealWebSocketEvent,
                )
            }
        }
        releaseAssistantMicrophone("streaming_stopped:$reason", resumeWakeWord = false)
        val stoppedConversationId = mutableState.value.streamingSessionId
        if (stoppedConversationId != null) {
            assistantTurnContextStore.clear(stoppedConversationId)
        } else if (
            mutableState.value.activeEntrySource == AssistantEntrySource.WakeWord ||
            mutableState.value.activeEntrySource == AssistantEntrySource.StreamingButton
        ) {
            assistantTurnContextStore.clear()
        }
        mutableState.value = mutableState.value.copy(
            phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
            audio = AssistantAudioStatus.Idle,
            activeEntrySource = null,
            microphoneOwner = MicrophoneOwner.None,
            streamingConversationState = StreamingConversationState.Inactive,
            streamingSessionActive = false,
            streamingSessionId = null,
            bargeInMonitorActive = false,
            vadState = VoiceActivityState.Disabled,
            vadStatusText = "流式对话已结束：$reason",
            statusText = if (wasActive) "流式对话已结束：$reason" else mutableState.value.statusText,
            lastEventAt = nowMillis(),
        )
        pttResponseExpected = false
        if (resumeWakeWord) {
            wakeWordAudioGate.resumeAfterAssistant("流式对话已结束：$reason")
        }
    }

    private suspend fun startBargeInMonitor(generation: Long) {
        val current = mutableState.value
        if (!isStreamingGenerationActive(generation) ||
            !current.streamingBargeInEnabled ||
            current.bargeInMonitorActive ||
            bargeInStarting ||
            realAudioEngine.isRecording() ||
            !realAudioEngine.isPlaybackActive()
        ) {
            return
        }

        bargeInStarting = true
        try {
            if (!prepareAssistantCapture(
                    current.activeEntrySource ?: AssistantEntrySource.StreamingButton,
                    "streaming_barge_in_monitor",
                )
            ) {
                return
            }
            if (!isStreamingGenerationActive(generation) || !realAudioEngine.isPlaybackActive()) {
                releaseAssistantMicrophone("barge_in_playback_already_finished", resumeWakeWord = false)
                return
            }

            streamingTurnToken += 1L
            val turnToken = streamingTurnToken
            bargeInMonitorGeneration += 1L
            val monitorGeneration = bargeInMonitorGeneration
            bargeInMonitorToken = turnToken
            bargeInTriggered = false
            realUploadedAudioFrames = 0

            val audioStart = realAudioEngine.startRecording(
                nowMillis = nowMillis(),
                onEncodedFrame = { packet ->
                    if (!bargeInTriggered) {
                        true
                    } else {
                        val sent = realWebSocketClient.sendAudioFrame(packet)
                        if (sent) realUploadedAudioFrames += 1
                        sent
                    }
                },
                captureConfig = VoiceCaptureConfig.bargeInMonitoring(),
                onVoiceActivity = { snapshot ->
                    onBargeInVoiceActivity(
                        snapshot = snapshot,
                        streamingSessionGeneration = generation,
                        turnToken = turnToken,
                        monitorGeneration = monitorGeneration,
                    )
                },
                onAutomaticStop = { reason ->
                    scope.launch {
                        handleBargeInAutomaticStop(
                            reason = reason,
                            streamingSessionGeneration = generation,
                            turnToken = turnToken,
                            monitorGeneration = monitorGeneration,
                        )
                    }
                },
            )
            if (!audioStart.started) {
                bargeInMonitorToken = -1L
                releaseAssistantMicrophone("barge_in_audio_start_failed", resumeWakeWord = false)
                mutableState.value = mutableState.value.copy(
                    bargeInMonitorActive = false,
                    statusText = "插话监听启动失败，继续播放当前回复",
                    lastAudioSummary = audioStart.summary,
                    lastEventAt = nowMillis(),
                )
                return
            }

            mutableState.value = mutableState.value.copy(
                microphoneOwner = MicrophoneOwner.AssistantCapture,
                bargeInMonitorActive = true,
                vadState = VoiceActivityState.Warmup,
                vadStatusText = "TTS 插话监听预热中",
                statusText = "助手回复中，可直接说话插话",
                lastAudioSummary = audioStart.summary,
                lastEventAt = nowMillis(),
            )
        } finally {
            bargeInStarting = false
        }
    }

    private fun onBargeInVoiceActivity(
        snapshot: VoiceActivitySnapshot,
        streamingSessionGeneration: Long,
        turnToken: Long,
        monitorGeneration: Long,
    ) {
        if (!isStreamingGenerationActive(streamingSessionGeneration) ||
            streamingTurnToken != turnToken ||
            bargeInMonitorToken != turnToken ||
            bargeInMonitorGeneration != monitorGeneration
        ) {
            return
        }

        mutableState.value = mutableState.value.copy(
            vadState = snapshot.state,
            vadStatusText = "插话 ${snapshot.state.storageValue} · peak=${snapshot.peakAbs} rms=${snapshot.rms} · ${snapshot.elapsedMs}ms",
            lastEventAt = nowMillis(),
        )
        if (snapshot.state == VoiceActivityState.SpeechDetected && !bargeInTriggered) {
            bargeInTriggered = true
            scope.launch {
                activateBargeIn(
                    streamingSessionGeneration = streamingSessionGeneration,
                    turnToken = turnToken,
                    monitorGeneration = monitorGeneration,
                )
            }
        }
    }

    private suspend fun activateBargeIn(
        streamingSessionGeneration: Long,
        turnToken: Long,
        monitorGeneration: Long,
    ) {
        if (!isStreamingGenerationActive(streamingSessionGeneration) ||
            streamingTurnToken != turnToken ||
            bargeInMonitorToken != turnToken ||
            bargeInMonitorGeneration != monitorGeneration
        ) {
            return
        }

        responsePlaybackIdleJob?.cancel()
        streamingNextTurnJob?.cancel()
        streamingResponseWatchdogJob?.cancel()
        realAudioEngine.stopPlaybackAndRelease()
        val aborted = realWebSocketClient.sendAbort(
            reason = "streaming_barge_in",
            onEvent = ::handleRealWebSocketEvent,
        )
        val listenStarted = realWebSocketClient.sendStartListening(
            mode = "manual",
            onEvent = ::handleRealWebSocketEvent,
        )
        if (!listenStarted) {
            stopBargeInMonitor("barge_in_listen_start_failed")
            stopStreamingConversationInternal(
                reason = "barge_in_listen_start_failed",
                resumeWakeWord = true,
                finalizeTransport = false,
            )
            return
        }

        val nextTurn = mutableState.value.streamingTurnIndex + 1
        mutableState.value = mutableState.value.copy(
            phase = AssistantPhase.Listening,
            audio = AssistantAudioStatus.Recording,
            streamingConversationState = StreamingConversationState.UserSpeaking,
            streamingTurnIndex = nextTurn,
            bargeInMonitorActive = true,
            bargeInTriggerCount = mutableState.value.bargeInTriggerCount + 1,
            vadState = VoiceActivityState.SpeechDetected,
            vadStatusText = "已检测到插话，正在接收第 $nextTurn 轮语音",
            statusText = "已停止当前回复并进入插话，abort=$aborted",
            lastEventAt = nowMillis(),
        )
    }

    private suspend fun handleBargeInAutomaticStop(
        reason: VoiceAutomaticStopReason,
        streamingSessionGeneration: Long,
        turnToken: Long,
        monitorGeneration: Long,
    ) {
        if (!isStreamingGenerationActive(streamingSessionGeneration) ||
            streamingTurnToken != turnToken ||
            bargeInMonitorToken != turnToken ||
            bargeInMonitorGeneration != monitorGeneration
        ) {
            return
        }
        if (!bargeInTriggered) {
            stopBargeInMonitor("barge_in_monitor_finished_without_speech")
            return
        }
        handleStreamingAutomaticStop(reason, streamingSessionGeneration, turnToken)
    }

    private suspend fun stopBargeInMonitor(reason: String) {
        if (bargeInMonitorToken < 0L && !bargeInStarting) return
        bargeInMonitorGeneration += 1L
        bargeInMonitorToken = -1L
        bargeInTriggered = false
        bargeInStarting = false
        if (realAudioEngine.isRecording()) {
            realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 1_500L)
        }
        releaseAssistantMicrophone("barge_in_monitor_stopped:$reason", resumeWakeWord = false)
        mutableState.value = mutableState.value.copy(
            bargeInMonitorActive = false,
            microphoneOwner = MicrophoneOwner.None,
            vadState = VoiceActivityState.Disabled,
            vadStatusText = "插话监听已停止：$reason",
            lastEventAt = nowMillis(),
        )
    }

    private suspend fun prepareAssistantCapture(
        source: AssistantEntrySource,
        reason: String,
    ): Boolean {
        pttWakeWordResumeJob?.cancel()
        val paused = wakeWordAudioGate.pauseForAssistant("助手准备使用麦克风：$reason")
        if (!paused) {
            mutableState.value = stateMachine.error(
                mutableState.value.copy(audio = AssistantAudioStatus.Error),
                "唤醒词 AudioRecord 未及时释放，未启动助手录音",
                nowMillis(),
            )
            return false
        }
        assistantMicrophoneLease?.let { existing ->
            microphoneOwnershipCoordinator.release(existing, "replace_assistant_capture")
            assistantMicrophoneLease = null
        }
        val lease = microphoneOwnershipCoordinator.acquire(
            MicrophoneOwner.AssistantCapture,
            "assistant:${source.storageValue}:$reason",
            timeoutMs = MICROPHONE_ACQUIRE_TIMEOUT_MS,
        )
        if (lease == null) {
            wakeWordAudioGate.resumeAfterAssistant("助手未取得麦克风")
            mutableState.value = stateMachine.error(
                mutableState.value.copy(audio = AssistantAudioStatus.Error),
                "麦克风正被其他组件占用",
                nowMillis(),
            )
            return false
        }
        assistantMicrophoneLease = lease
        mutableState.value = mutableState.value.copy(
            activeEntrySource = source,
            microphoneOwner = MicrophoneOwner.AssistantCapture,
            errorMessage = null,
        )
        return true
    }

    private suspend fun releaseAssistantMicrophone(reason: String, resumeWakeWord: Boolean) {
        assistantMicrophoneLease?.let { lease ->
            microphoneOwnershipCoordinator.release(lease, reason)
        }
        assistantMicrophoneLease = null
        mutableState.value = mutableState.value.copy(microphoneOwner = MicrophoneOwner.None)
        if (resumeWakeWord) wakeWordAudioGate.resumeAfterAssistant(reason)
    }

    private fun schedulePushToTalkWakeWordResume(delayMs: Long) {
        pttWakeWordResumeJob?.cancel()
        pttWakeWordResumeJob = scope.launch {
            delay(delayMs)
            if (!mutableState.value.streamingSessionActive && !realAudioEngine.isRecording()) {
                responsePlaybackIdleJob?.cancel()
                realAudioEngine.stopPlaybackAndRelease()
                assistantTurnContextStore.clear()
                mutableState.value = mutableState.value.copy(
                    phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                    audio = AssistantAudioStatus.Idle,
                    activeEntrySource = null,
                    statusText = if (mutableState.value.isConnected) "助手回复完成，在线待命" else "助手回复完成，待命",
                    lastEventAt = nowMillis(),
                )
                pttResponseExpected = false
                wakeWordAudioGate.resumeAfterAssistant("按住说话回复已完成或超时")
            }
        }
    }

    private fun schedulePlaybackIdleCompletion() {
        responsePlaybackIdleJob?.cancel()
        responsePlaybackIdleJob = scope.launch {
            delay(RESPONSE_PLAYBACK_IDLE_MS)
            val current = mutableState.value
            if (current.phase != AssistantPhase.Speaking && current.audio != AssistantAudioStatus.Playing) {
                return@launch
            }
            realAudioEngine.stopPlaybackAndRelease()
            if (current.streamingSessionActive) {
                val generation = streamingGeneration
                if (current.bargeInMonitorActive && !bargeInTriggered) {
                    stopBargeInMonitor("playback_idle_without_barge_in")
                }
                val latest = mutableState.value
                mutableState.value = latest.copy(
                    phase = AssistantPhase.Connected,
                    audio = AssistantAudioStatus.Idle,
                    bargeInMonitorActive = false,
                    streamingConversationState = StreamingConversationState.WaitingForNextTurn,
                    statusText = "助手回复播放完成，准备下一轮",
                    lastEventAt = nowMillis(),
                )
                if (isStreamingGenerationActive(generation) && !realAudioEngine.isRecording()) {
                    scheduleNextStreamingTurn(generation, STREAMING_TTS_RESUME_MS)
                }
            } else {
                assistantTurnContextStore.clear()
                mutableState.value = current.copy(
                    phase = if (current.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
                    audio = AssistantAudioStatus.Idle,
                    activeEntrySource = null,
                    statusText = if (current.isConnected) "助手回复完成，在线待命" else "助手回复完成，待命",
                    lastEventAt = nowMillis(),
                )
                pttResponseExpected = false
                wakeWordAudioGate.resumeAfterAssistant("回复音频空闲，恢复待命")
            }
        }
    }

    private fun isStreamingGenerationActive(generation: Long): Boolean =
        mutableState.value.streamingSessionActive && streamingGeneration == generation

    override suspend fun handleSystemAudioInterruption(
        reason: String,
        resumeWakeWord: Boolean,
    ) {
        pttGeneration += 1L
        responsePlaybackIdleJob?.cancel()
        streamingNextTurnJob?.cancel()
        streamingResponseWatchdogJob?.cancel()
        pttWakeWordResumeJob?.cancel()

        val current = mutableState.value
        val hadVoiceActivity = current.streamingSessionActive ||
            current.activeEntrySource != null ||
            current.audio != AssistantAudioStatus.Idle ||
            realAudioEngine.isRecording() ||
            realAudioEngine.isPlaybackActive()

        if (hadVoiceActivity && realWebSocketClient.isConnected()) {
            realWebSocketClient.sendAbort(
                reason = "system_audio:${reason.take(48)}",
                onEvent = ::handleRealWebSocketEvent,
            )
        }

        if (current.streamingSessionActive) {
            stopStreamingConversationInternal(
                reason = "system_audio:$reason",
                resumeWakeWord = false,
                finalizeTransport = false,
            )
        } else {
            if (realAudioEngine.isRecording()) {
                realAudioEngine.stopRecordingAndAwait(maxStopLatencyMs = 1_500L)
            }
            releaseAssistantMicrophone("system_audio:$reason", resumeWakeWord = false)
            assistantTurnContextStore.clear()
        }

        realAudioEngine.stopPlaybackAndRelease()
        pttResponseExpected = false
        mutableState.value = mutableState.value.copy(
            phase = if (mutableState.value.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
            audio = AssistantAudioStatus.Idle,
            activeEntrySource = null,
            microphoneOwner = MicrophoneOwner.None,
            bargeInMonitorActive = false,
            statusText = "系统音频中断，语音会话已安全释放：$reason",
            lastAudioSummary = "Phase5-04 v3 system audio interruption: $reason",
            lastEventAt = nowMillis(),
        )
        if (resumeWakeWord) {
            wakeWordAudioGate.resumeAfterAssistant("系统音频中断已处理：$reason")
        }
    }

    override suspend fun handleSystemAudioRecovered(reason: String) {
        val current = mutableState.value
        if (current.streamingSessionActive ||
            current.microphoneOwner != MicrophoneOwner.None ||
            realAudioEngine.isRecording()
        ) {
            return
        }
        wakeWordAudioGate.resumeAfterAssistant("系统音频已恢复：$reason")
        mutableState.value = current.copy(
            phase = if (current.isConnected) AssistantPhase.Connected else AssistantPhase.Idle,
            audio = AssistantAudioStatus.Idle,
            statusText = if (current.isConnected) {
                "系统音频已恢复，助手在线待命"
            } else {
                "系统音频已恢复，助手待命"
            },
            errorMessage = null,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun simulateIncomingToolCall(toolName: String, argumentsJson: String) {
        if (!fakeWebSocketClient.isConnected()) connectFake()
        val turn = fakeWebSocketClient.simulateIncomingToolCall(toolName, argumentsJson)
        val trace = (turn.event as? ProtocolEvent.McpResponse)?.toToolTrace()
        mutableState.value = mutableState.value.copy(
            lastAssistantText = turn.message,
            statusText = if (turn.success) "MCP tools/call 已处理：$toolName" else turn.message,
            lastServerJson = turn.incomingJson,
            lastClientJson = turn.outgoingResponseJson,
            lastProtocolEvent = turn.event.javaClass.simpleName,
            lastToolName = trace?.toolName ?: toolName,
            lastToolStatus = trace?.status,
            lastCommandLogId = trace?.commandLogId,
            lastConfirmationId = trace?.confirmationId,
            lastEventAt = nowMillis(),
        )
    }

    override suspend fun simulateIncomingToolsList() {
        if (!fakeWebSocketClient.isConnected()) connectFake()
        val turn = fakeWebSocketClient.simulateIncomingToolsListRequest()
        mutableState.value = mutableState.value.copy(
            lastAssistantText = turn.message,
            statusText = if (turn.success) "MCP tools/list 已通过 runtime executor 返回 descriptors" else turn.message,
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
            phase4RealToolCallVerified = false,
            lastToolName = null,
            lastToolStatus = null,
            lastCommandLogId = null,
            lastConfirmationId = null,
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
        if (mutableState.value.streamingSessionActive) {
            stopStreamingConversationInternal(
                reason = "audio_failure",
                resumeWakeWord = true,
                finalizeTransport = false,
            )
        }
        releaseAssistantMicrophone("audio_failure", resumeWakeWord = false)
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
                    statusText = "真实 WebSocket hello/session 握手完成",
                    lastProtocolEvent = event.javaClass.simpleName,
                )
            }
            is XiaozhiWebSocketEvent.Closed -> {
                scope.launch { handleTransportClosed(event) }
            }
            is XiaozhiWebSocketEvent.Error -> {
                scope.launch {
                    if (mutableState.value.streamingSessionActive) {
                        stopStreamingConversationInternal(
                            reason = "websocket_error",
                            resumeWakeWord = true,
                            finalizeTransport = false,
                        )
                    }
                    releaseAssistantMicrophone("websocket_error", resumeWakeWord = false)
                    realAudioEngine.cancel()
                    mutableState.value = stateMachine.disconnected(
                        current = mutableState.value.copy(connection = AssistantConnectionStatus.Disconnected, sessionId = null),
                        reason = event.message,
                        nowMillis = nowMillis(),
                    ).copy(
                        runtimeMode = AssistantRuntimeMode.Real,
                        fakeRuntime = false,
                        errorMessage = null,
                        statusText = "连接中断，正在自动恢复",
                        lastProtocolEvent = event.javaClass.simpleName,
                        lastReconnectDecision = "real_websocket_error",
                    )
                    scheduleAutomaticReconnect("websocket_error")
                }
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
                    statusText = "真实服务端 hello 已解析，session=${event.sessionId}",
                )
            }
            is ProtocolEvent.AssistantText -> {
                val messageType = runCatching { JSONObject(rawJson).optString("type") }.getOrDefault("")
                if (messageType == "stt") {
                    mutableState.value = base.copy(
                        lastUserText = event.text,
                        statusText = "已识别语音",
                    )
                } else {
                    val streamingActive = base.streamingSessionActive
                    mutableState.value = stateMachine.assistantText(base, event.text, nowMillis()).copy(
                        runtimeMode = AssistantRuntimeMode.Real,
                        fakeRuntime = false,
                        gateBRealTextVerified = true,
                        streamingConversationState = if (streamingActive) {
                            StreamingConversationState.Thinking
                        } else {
                            base.streamingConversationState
                        },
                        statusText = "收到助手回复",
                    )
                    if (streamingActive) {
                        scheduleNextStreamingTurn(streamingGeneration, STREAMING_TEXT_ONLY_RESUME_MS)
                    } else {
                        schedulePushToTalkWakeWordResume(RESPONSE_TEXT_RESUME_MS)
                    }
                }
            }
            is ProtocolEvent.TtsState -> {
                val ended = event.state == "stop" || event.state == "end"
                val streamingActive = base.streamingSessionActive
                val receivingBargeIn = streamingActive && base.bargeInMonitorActive && bargeInTriggered
                if (!ended && receivingBargeIn) {
                    mutableState.value = base.copy(
                        phase = AssistantPhase.Listening,
                        audio = AssistantAudioStatus.Recording,
                        streamingConversationState = StreamingConversationState.UserSpeaking,
                        bargeInMonitorActive = true,
                        statusText = "已忽略旧回复的迟到 TTS 状态，正在接收插话",
                    )
                } else if (ended && receivingBargeIn) {
                    responsePlaybackIdleJob?.cancel()
                    realAudioEngine.stopPlaybackAndRelease()
                    mutableState.value = base.copy(
                        phase = AssistantPhase.Listening,
                        audio = AssistantAudioStatus.Recording,
                        streamingConversationState = StreamingConversationState.UserSpeaking,
                        bargeInMonitorActive = true,
                        statusText = "旧回复已停止，正在接收插话",
                    )
                } else if (ended) {
                    responsePlaybackIdleJob?.cancel()
                    realAudioEngine.stopPlaybackAndRelease()
                    mutableState.value = base.copy(
                        phase = AssistantPhase.Connected,
                        audio = AssistantAudioStatus.Idle,
                        streamingConversationState = if (streamingActive) {
                            StreamingConversationState.WaitingForNextTurn
                        } else {
                            base.streamingConversationState
                        },
                        statusText = if (streamingActive) "助手回复完成，准备下一轮" else "助手回复完成，在线待命",
                    )
                    if (streamingActive) {
                        val generation = streamingGeneration
                        scope.launch {
                            stopBargeInMonitor("tts_finished_without_barge_in")
                            if (isStreamingGenerationActive(generation) && !realAudioEngine.isRecording()) {
                                scheduleNextStreamingTurn(generation, STREAMING_TTS_RESUME_MS)
                            }
                        }
                    } else {
                        schedulePushToTalkWakeWordResume(RESPONSE_TTS_RESUME_MS)
                    }
                } else {
                    responsePlaybackIdleJob?.cancel()
                    streamingNextTurnJob?.cancel()
                    pttWakeWordResumeJob?.cancel()
                    mutableState.value = base.copy(
                        phase = AssistantPhase.Speaking,
                        audio = AssistantAudioStatus.Playing,
                        streamingConversationState = if (streamingActive) {
                            StreamingConversationState.Speaking
                        } else {
                            base.streamingConversationState
                        },
                        statusText = "真实 TTS state=${event.state}",
                    )
                    if (streamingActive && base.streamingBargeInEnabled) {
                        val generation = streamingGeneration
                        scope.launch { startBargeInMonitor(generation) }
                    }
                    schedulePlaybackIdleCompletion()
                }
            }
            is ProtocolEvent.ListenState -> {
                mutableState.value = base.copy(statusText = "真实 listen state=${event.state}")
            }
            is ProtocolEvent.McpResponse -> {
                val sent = realWebSocketClient.sendMcpResponse(event.responseJson, ::handleRealWebSocketEvent)
                val trace = event.toToolTrace()
                mutableState.value = base.copy(
                    lastAssistantText = event.message,
                    statusText = "真实服务端 MCP ${event.requestMethod ?: "request"} 已通过 Phase4 工具链处理，response_sent=$sent",
                    phase4RealToolCallVerified = base.phase4RealToolCallVerified || (sent && trace.toolName != null),
                    lastToolName = trace.toolName,
                    lastToolStatus = trace.status,
                    lastCommandLogId = trace.commandLogId,
                    lastConfirmationId = trace.confirmationId,
                )
            }
            is ProtocolEvent.ProtocolError -> {
                mutableState.value = base.copy(
                    statusText = "真实协议消息解析失败：${event.reason}",
                    errorMessage = "protocol_error=${event.reason}",
                )
            }
            is ProtocolEvent.RawJson -> {
                mutableState.value = base.copy(statusText = "收到真实未处理消息 type=${event.type}")
            }
            is ProtocolEvent.ToolCall -> {
                mutableState.value = base.copy(statusText = "收到旧式 ToolCall 事件 ${event.toolName}；请使用 MCP tools/call。")
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
        if (bargeInTriggered && mutableState.value.bargeInMonitorActive) {
            mutableState.value = mutableState.value.copy(
                statusText = "插话期间忽略旧回复的迟到音频包",
                lastEventAt = nowMillis(),
            )
            return
        }
        streamingNextTurnJob?.cancel()
        pttWakeWordResumeJob?.cancel()
        scope.launch {
            val before = stateMachine.speaking(mutableState.value, nowMillis()).copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                lastProtocolEvent = "IncomingBinary",
                streamingConversationState = if (mutableState.value.streamingSessionActive) {
                    StreamingConversationState.Speaking
                } else {
                    mutableState.value.streamingConversationState
                },
                statusText = "收到真实服务端二进制音频，正在解码播放",
            )
            mutableState.value = before
            val playback = realAudioEngine.playIncomingOpusFrame(bytes)
            mutableState.value = mutableState.value.copy(
                runtimeMode = AssistantRuntimeMode.Real,
                fakeRuntime = false,
                audio = if (playback.success) AssistantAudioStatus.Playing else mutableState.value.audio,
                lastAudioSummary = playback.summary,
                gateBRealAudioPlaybackVerified = mutableState.value.gateBRealAudioPlaybackVerified || playback.success,
                statusText = if (playback.success) "真实服务端 audio/TTS 已解码并写入 AudioTrack" else "真实服务端音频帧已收到，等待可播放 PCM",
                lastEventAt = nowMillis(),
            )
            if (playback.success) {
                schedulePlaybackIdleCompletion()
                val current = mutableState.value
                if (current.streamingSessionActive && current.streamingBargeInEnabled && !current.bargeInMonitorActive) {
                    startBargeInMonitor(streamingGeneration)
                }
            }
        }
    }

    private suspend fun handleTransportClosed(closed: XiaozhiWebSocketEvent.Closed) {
        responsePlaybackIdleJob?.cancel()
        pttResponseExpected = false
        val beforeCleanup = mutableState.value
        if (beforeCleanup.streamingSessionActive) {
            stopStreamingConversationInternal(
                reason = "transport_closed",
                resumeWakeWord = true,
                finalizeTransport = false,
            )
        }
        releaseAssistantMicrophone("transport_closed", resumeWakeWord = false)
        realAudioEngine.cancel()
        val current = mutableState.value
        val decision = reconnectPolicy.decideClose(
            closeCode = closed.code,
            reason = closed.reason,
            assistantEnabled = current.assistantEnabled,
            currentAttempt = current.reconnectAttempt,
        )
        mutableState.value = stateMachine.disconnected(current, closed.reason, nowMillis()).copy(
            runtimeMode = current.runtimeMode,
            fakeRuntime = current.runtimeMode == AssistantRuntimeMode.Fake,
            errorMessage = null,
            lastCloseCode = closed.code,
            lastCloseReason = closed.reason,
            lastReconnectDecision = decision.decisionLabel,
            statusText = if (decision.shouldReconnect) "连接中断，正在自动恢复" else "语音服务已断开",
        )
        if (decision.shouldReconnect) {
            scheduleAutomaticReconnect("transport_closed")
        }
    }

    private fun scheduleAutomaticReconnect(reason: String) {
        if (manualDisconnectRequested || !mutableState.value.assistantEnabled) return
        if (mutableState.value.runtimeMode != AssistantRuntimeMode.Real) return
        if (automaticReconnectJob?.isActive == true) return

        val nextAttempt = mutableState.value.reconnectAttempt + 1
        if (nextAttempt > MAX_AUTOMATIC_RECONNECT_ATTEMPTS) {
            mutableState.value = stateMachine.disconnected(
                current = mutableState.value,
                reason = reason,
                nowMillis = nowMillis(),
            ).copy(
                errorMessage = null,
                statusText = "暂时无法连接，点击小智重试",
                lastReconnectDecision = "automatic_reconnect_exhausted",
            )
            return
        }

        val delayMs = automaticReconnectDelayMs(nextAttempt)
        mutableState.value = stateMachine.reconnecting(
            current = mutableState.value,
            reason = reason,
            attempt = nextAttempt,
            nowMillis = nowMillis(),
        ).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            errorMessage = null,
            statusText = "正在重新连接",
            lastReconnectDecision = "automatic_reconnect_$nextAttempt",
        )
        automaticReconnectJob = scope.launch {
            delay(delayMs)
            automaticReconnectJob = null
            if (manualDisconnectRequested || !mutableState.value.assistantEnabled) return@launch
            connectReal()
            if (!mutableState.value.isConnected) {
                scheduleAutomaticReconnect(reason)
            }
        }
    }

    private fun automaticReconnectDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        4 -> 8_000L
        else -> 15_000L
    }

    private fun ProtocolEvent.McpResponse.toToolTrace(): ToolTrace {
        val structured = runCatching {
            JSONObject(responseJson)
                .optJSONObject("result")
                ?.optJSONObject("structuredContent")
        }.getOrNull()
        return ToolTrace(
            toolName = toolName ?: structured?.optString("tool_name")?.ifBlank { null },
            status = structured?.optString("status")?.ifBlank { status.storageValue } ?: status.storageValue,
            commandLogId = structured.optNullableLong("command_log_id"),
            confirmationId = structured?.optString("confirmation_id")?.takeUnless { it.isBlank() || it == "null" },
        )
    }

    private fun JSONObject?.optNullableLong(name: String): Long? {
        if (this == null || !has(name) || isNull(name)) return null
        return optLong(name, 0L).takeIf { it > 0L }
    }

    private data class ToolTrace(
        val toolName: String?,
        val status: String?,
        val commandLogId: Long?,
        val confirmationId: String?,
    )

    private fun nowMillis(): Long = System.currentTimeMillis()

    private companion object {
        const val MICROPHONE_ACQUIRE_TIMEOUT_MS = 3_000L
        const val MIN_STREAMING_OPUS_PACKETS = 6
        const val STREAMING_RESPONSE_TIMEOUT_MS = 20_000L
        const val STREAMING_TEXT_ONLY_RESUME_MS = 2_500L
        const val STREAMING_TTS_RESUME_MS = 350L
        const val STREAMING_EMPTY_TURN_RETRY_MS = 500L
        const val STREAMING_RECOVERY_DELAY_MS = 500L
        const val RESPONSE_TEXT_RESUME_MS = 3_000L
        const val RESPONSE_TTS_RESUME_MS = 500L
        const val RESPONSE_FALLBACK_RESUME_MS = 12_000L
        const val RESPONSE_PLAYBACK_IDLE_MS = 1_500L
        const val MAX_AUTOMATIC_RECONNECT_ATTEMPTS = 5
    }
}
