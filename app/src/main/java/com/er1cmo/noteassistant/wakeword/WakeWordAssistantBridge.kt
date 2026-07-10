package com.er1cmo.noteassistant.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantRuntimeMode
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordCoordinator
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordEvent
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class WakeWordAssistantBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wakeWordCoordinator: WakeWordCoordinator,
    private val wakeWordServiceController: WakeWordServiceController,
    private val assistantController: AssistantController,
    private val acknowledgementPlayer: WakeWordAcknowledgementPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private val handoffRunning = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            wakeWordCoordinator.detections.collect(::handleDetection)
        }
        scope.launch {
            var lastNotifiedConfirmationId: String? = null
            assistantController.state.collect { state ->
                val confirmationId = state.lastConfirmationId
                if (
                    confirmationId != null &&
                    confirmationId != lastNotifiedConfirmationId &&
                    state.activeEntrySource == AssistantEntrySource.WakeWord
                ) {
                    lastNotifiedConfirmationId = confirmationId
                    wakeWordServiceController.showAssistantStatus("有待确认的便签操作，点击打开应用")
                }
                if (confirmationId == null) lastNotifiedConfirmationId = null
            }
        }
    }

    private suspend fun handleDetection(event: WakeWordEvent.Detected) {
        if (!handoffRunning.compareAndSet(false, true)) return
        try {
            val current = assistantController.state.value
            if (current.streamingSessionActive) return
            if (!hasRecordAudioPermission()) {
                wakeWordServiceController.showAssistantStatus("已唤醒，但缺少麦克风权限")
                wakeWordServiceController.resume()
                return
            }

            wakeWordServiceController.showAssistantStatus("已唤醒：${event.rawKeyword}，正在准备助手")
            if (!current.assistantEnabled) assistantController.enableAssistant()
            if (assistantController.state.value.runtimeMode != AssistantRuntimeMode.Real) {
                assistantController.useRealRuntime()
            }
            assistantController.ensureDeviceIdentity()

            if (assistantController.state.value.activation != AssistantActivationStatus.Activated) {
                wakeWordServiceController.showAssistantStatus("已唤醒，正在检查设备激活")
                assistantController.runRealActivation()
            }
            if (assistantController.state.value.activation != AssistantActivationStatus.Activated) {
                wakeWordServiceController.showAssistantStatus("需要打开应用完成设备激活")
                wakeWordServiceController.resume()
                return
            }

            if (!assistantController.state.value.isConnected) {
                wakeWordServiceController.showAssistantStatus("已唤醒，正在连接语音服务")
                assistantController.connect()
            }
            if (!assistantController.state.value.isConnected) {
                wakeWordServiceController.showAssistantStatus("语音服务连接失败，已恢复唤醒监听")
                wakeWordServiceController.resume()
                return
            }

            // KWS has already released AudioRecord before Detected is emitted. Play the
            // acknowledgement before assistant capture so the tone is never uploaded.
            acknowledgementPlayer.playAndAwait()
            assistantController.startStreamingConversation(
                hasRecordAudioPermission = true,
                source = AssistantEntrySource.WakeWord,
                wakeKeyword = event.rawKeyword,
            )
            if (assistantController.state.value.streamingSessionActive) {
                wakeWordServiceController.showAssistantStatus("已唤醒，流式对话正在聆听")
            } else {
                wakeWordServiceController.showAssistantStatus("流式对话未启动，已恢复唤醒监听")
                wakeWordServiceController.resume()
            }
        } catch (error: Throwable) {
            wakeWordServiceController.showAssistantStatus(
                "唤醒交接失败：${error.message ?: error::class.java.simpleName}",
            )
            runCatching { wakeWordServiceController.resume() }
        } finally {
            handoffRunning.set(false)
        }
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
}

@Singleton
class WakeWordAcknowledgementPlayer @Inject constructor() {
    suspend fun playAndAwait() = withContext(Dispatchers.Default) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS.toInt())
            delay(TONE_CAPTURE_GUARD_MS)
        } finally {
            runCatching { tone?.release() }
        }
    }

    private companion object {
        const val TONE_DURATION_MS = 120L
        const val TONE_CAPTURE_GUARD_MS = 220L
    }
}
