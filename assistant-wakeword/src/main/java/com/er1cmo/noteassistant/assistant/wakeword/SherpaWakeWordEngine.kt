package com.er1cmo.noteassistant.assistant.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.core.common.audio.MicrophoneLease
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwner
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwnershipCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SherpaWakeWordEngine(
    private val context: Context,
    private val config: WakeWordConfig,
    private val microphoneOwnershipCoordinator: MicrophoneOwnershipCoordinator,
    private val onEvent: (WakeWordEvent) -> Unit,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var job: Job? = null
    private var lastDetectedAt = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = engineScope.launch { runLoop() }
    }

    suspend fun stopAndAwait() {
        running.set(false)
        job?.cancelAndJoin()
        job = null
    }

    fun release() {
        running.set(false)
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            running.set(false)
            emit(WakeWordEvent.Error("本地唤醒词启动失败：缺少麦克风权限", "permission_missing"))
            return
        }

        var lease: MicrophoneLease? = null
        var detector: SherpaWakeWordDetector? = null
        var audioRecord: AudioRecord? = null
        var pendingDetection: WakeWordEvent.Detected? = null
        try {
            lease = microphoneOwnershipCoordinator.acquire(
                owner = MicrophoneOwner.WakeWordKws,
                reason = "wakeword:${config.phrase.displayText}",
                timeoutMs = MICROPHONE_ACQUIRE_TIMEOUT_MS,
            )
            if (lease == null) {
                running.set(false)
                emit(WakeWordEvent.Error(
                    "本地唤醒词暂未取得麦克风：当前由助手录音占用",
                    "microphone_busy",
                ))
                return
            }

            emit(WakeWordEvent.Status(
                message = "本地唤醒词模型初始化中：${config.phrase.displayText}",
                state = "initializing",
                keyword = config.phrase.displayText,
            ))
            detector = SherpaWakeWordDetector(context, config)
            detector.initialize()

            val minBufferBytes = AudioRecord.getMinBufferSize(
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(config.samplesPerFrame * 4)
            audioRecord = createAudioRecord(minBufferBytes)
            audioRecord.startRecording()
            emit(WakeWordEvent.Status(
                message = "本地唤醒词监听中：${config.phrase.displayText}",
                state = "listening",
                keyword = config.phrase.displayText,
            ))

            val shortBuffer = ShortArray(config.samplesPerFrame)
            while (running.get()) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) {
                    delay(20L)
                    continue
                }
                val samples = FloatArray(read) { index -> shortBuffer[index] / 32768.0f }
                val detected = detector.accept(samples)
                if (detected != null) {
                    pendingDetection = handleDetection(detected, detector)
                }
            }
        } catch (error: UnsatisfiedLinkError) {
            running.set(false)
            emit(WakeWordEvent.Error(
                "本地唤醒词启动失败：缺少 sherpa-onnx-jni 原生库，请检查 assistant-wakeword/src/main/jniLibs。",
                "jni_missing",
            ))
        } catch (exception: Throwable) {
            running.set(false)
            emit(WakeWordEvent.Error(
                "本地唤醒词异常：${exception.message ?: exception::class.java.simpleName}",
                "engine_error",
            ))
        } finally {
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            runCatching { detector?.release() }
            lease?.let {
                microphoneOwnershipCoordinator.release(it, "wakeword_audio_released")
            }
            emit(WakeWordEvent.Status(
                message = "本地唤醒词 AudioRecord 已释放",
                state = "audio_released",
                keyword = config.phrase.displayText,
            ))
        }

        pendingDetection?.let { detection ->
            delay(config.callbackDelayAfterHitMs)
            emit(detection)
        }
    }

    private suspend fun handleDetection(
        result: WakeWordDetectionResult,
        detector: SherpaWakeWordDetector,
    ): WakeWordEvent.Detected? {
        val now = System.currentTimeMillis()
        if (now - lastDetectedAt < config.cooldownMs) {
            detector.resetStream()
            emit(WakeWordEvent.Status(
                message = "唤醒词命中但仍在冷却：${result.keyword}",
                state = "cooldown",
                keyword = result.keyword,
                latencyMs = result.latencyMs,
            ))
            return null
        }
        lastDetectedAt = now
        running.set(false)
        return WakeWordEvent.Detected(
            keyword = normalizeWakeWordHit(config.phrase.displayText),
            rawKeyword = result.keyword,
            source = "sherpa-onnx-kws",
            latencyMs = result.latencyMs,
            keywordsScore = config.keywordsScore,
            keywordsThreshold = config.keywordsThreshold,
            cooldownMs = config.cooldownMs,
        )
    }

    private fun createAudioRecord(minBufferBytes: Int): AudioRecord {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )
        var lastFailure: Throwable? = null
        sources.forEach { source ->
            val record = runCatching {
                AudioRecord(
                    source,
                    config.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferBytes,
                )
            }.onFailure { lastFailure = it }.getOrNull()
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                emitAsync(WakeWordEvent.Status(
                    message = "唤醒词麦克风已打开：source=$source, ${config.sampleRate}Hz",
                    state = "audio_open",
                    keyword = config.phrase.displayText,
                ))
                return record
            }
            runCatching { record?.release() }
        }
        throw IllegalStateException("无法创建唤醒词 AudioRecord：${lastFailure?.message.orEmpty()}")
    }

    private suspend fun emit(event: WakeWordEvent) {
        withContext(Dispatchers.Main.immediate) { onEvent(event) }
    }

    private fun emitAsync(event: WakeWordEvent) {
        engineScope.launch(Dispatchers.Main.immediate) { onEvent(event) }
    }

    private companion object {
        const val MICROPHONE_ACQUIRE_TIMEOUT_MS = 2_500L
    }
}
