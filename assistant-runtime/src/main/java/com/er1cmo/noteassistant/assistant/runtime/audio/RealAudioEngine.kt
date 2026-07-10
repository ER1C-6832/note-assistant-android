package com.er1cmo.noteassistant.assistant.runtime.audio

import android.media.AudioRecord
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceActivityState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class VoiceCaptureConfig(
    val activityConfig: VoiceActivityConfig = VoiceActivityConfig.Disabled,
    val stopOnEndOfSpeech: Boolean = false,
    val stopOnNoSpeechTimeout: Boolean = false,
    val suppressUplinkDuringPlayback: Boolean = false,
) {
    companion object {
        val Manual = VoiceCaptureConfig()
        fun streaming(idleTimeoutMs: Long): VoiceCaptureConfig = VoiceCaptureConfig(
            activityConfig = VoiceActivityConfig.streaming(idleTimeoutMs),
            stopOnEndOfSpeech = true,
            stopOnNoSpeechTimeout = true,
            suppressUplinkDuringPlayback = true,
        )
    }
}

enum class VoiceAutomaticStopReason {
    EndOfSpeech,
    NoSpeechTimeout,
}

@Singleton
class RealAudioEngine @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val encodedFramesLock = Any()
    private val encodedFrames = mutableListOf<ByteArray>()
    private val playbackLock = Any()

    @Volatile private var recording = false
    @Volatile private var activeAudioRecord: AudioRecord? = null
    @Volatile private var recordingJob: Job? = null
    @Volatile private var startedAtMs: Long? = null
    @Volatile private var pcmFrames = 0
    @Volatile private var opusFrames = 0
    @Volatile private var failedUploads = 0
    @Volatile private var lastError: String? = null
    @Volatile private var processingSummary: String = "尚未启动真实录音"
    @Volatile private var peakAbs: Int = 0
    @Volatile private var squareSum: Double = 0.0
    @Volatile private var sampleCount: Long = 0L
    @Volatile private var silentFrames: Int = 0
    @Volatile private var captureGeneration: Long = 0L

    private var downlinkDecoder: AndroidOpusDecoder? = null
    private var downlinkPlayer: AndroidAudioPlayer? = null
    @Volatile private var downlinkPlaybackStarted: Boolean = false
    private var downlinkPcmFrames: Int = 0
    private var downlinkPcmBytes: Long = 0L
    private var downlinkOpusFrames: Int = 0

    fun isRecording(): Boolean = recording
    fun isPlaybackActive(): Boolean = downlinkPlaybackStarted

    fun startRecording(
        nowMillis: Long = System.currentTimeMillis(),
        onEncodedFrame: (ByteArray) -> Boolean,
        captureConfig: VoiceCaptureConfig = VoiceCaptureConfig.Manual,
        onVoiceActivity: (VoiceActivitySnapshot) -> Unit = {},
        onAutomaticStop: (VoiceAutomaticStopReason) -> Unit = {},
    ): RealAudioStartResult {
        if (recording) {
            return RealAudioStartResult(
                started = false,
                startedAtMs = startedAtMs ?: nowMillis,
                summary = "真实录音已经启动，忽略重复 start",
            )
        }

        synchronized(encodedFramesLock) { encodedFrames.clear() }
        captureGeneration += 1L
        val generation = captureGeneration
        recording = true
        startedAtMs = nowMillis
        pcmFrames = 0
        opusFrames = 0
        failedUploads = 0
        lastError = null
        peakAbs = 0
        squareSum = 0.0
        sampleCount = 0L
        silentFrames = 0
        processingSummary = "真实 AudioRecord / OpusEncoder 启动中"

        recordingJob = scope.launch {
            val recorder = AndroidAudioRecorder()
            var recordSession: AndroidAudioRecorder.RecordSession? = null
            var audioRecord: AudioRecord? = null
            var encoder: AndroidOpusEncoder? = null
            var automaticStopReason: VoiceAutomaticStopReason? = null
            var suppressedPlaybackFrames = 0
            val activityDetector = captureConfig.activityConfig
                .takeIf { it.enabled }
                ?.let(::VoiceActivityDetector)
            try {
                recordSession = recorder.createRecordSession(
                    AndroidAudioRecorder.AudioProcessingRequest(
                        enableAec = true,
                        enableNoiseSuppressor = true,
                        enableAgc = false,
                    ),
                )
                processingSummary = recordSession.processingReport.summary
                audioRecord = recordSession.audioRecord
                activeAudioRecord = audioRecord
                encoder = AndroidOpusEncoder()
                audioRecord.startRecording()

                val buffer = ByteArray(AudioConstants.PCM_FRAME_BYTES)
                while (recording && generation == captureGeneration) {
                    val ok = recorder.readPcmFrame(audioRecord, buffer)
                    if (!ok || !recording || generation != captureGeneration) break

                    inspectLevels(buffer)
                    pcmFrames += 1

                    val activity = activityDetector?.processPcm16(buffer)
                    if (activity != null) {
                        onVoiceActivity(activity)
                        when (activity.state) {
                            VoiceActivityState.EndOfSpeech -> if (captureConfig.stopOnEndOfSpeech) {
                                automaticStopReason = VoiceAutomaticStopReason.EndOfSpeech
                                recording = false
                            }
                            VoiceActivityState.NoSpeechTimeout -> if (captureConfig.stopOnNoSpeechTimeout) {
                                automaticStopReason = VoiceAutomaticStopReason.NoSpeechTimeout
                                recording = false
                            }
                            else -> Unit
                        }
                    }
                    if (!recording) break

                    if (captureConfig.suppressUplinkDuringPlayback && isPlaybackActive()) {
                        suppressedPlaybackFrames += 1
                        continue
                    }
                    suppressedPlaybackFrames = 0

                    val pcm = buffer.copyOf()
                    val frameIndex = pcmFrames - 1
                    val presentationTimeUs = frameIndex * AudioConstants.FRAME_DURATION_MS * 1_000L
                    val packets = encoder.encode(PcmFrame(pcm, presentationTimeUs))
                    for (packet in packets) {
                        if (packet.isEmpty()) continue
                        synchronized(encodedFramesLock) { encodedFrames += packet }
                        opusFrames += 1
                        if (!onEncodedFrame(packet)) failedUploads += 1
                    }
                }
            } catch (exception: Exception) {
                lastError = exception.message ?: exception::class.java.simpleName
            } finally {
                recording = false
                activeAudioRecord = null
                runCatching { audioRecord?.stop() }
                runCatching { recordSession?.release() }
                runCatching {
                    encoder?.release()?.forEach { packet ->
                        if (packet.isNotEmpty()) {
                            synchronized(encodedFramesLock) { encodedFrames += packet }
                            opusFrames += 1
                            if (!onEncodedFrame(packet)) failedUploads += 1
                        }
                    }
                }
                automaticStopReason?.let(onAutomaticStop)
            }
        }

        return RealAudioStartResult(
            started = true,
            startedAtMs = nowMillis,
            summary = buildString {
                append("真实 AudioRecord -> MediaCodec Opus -> WebSocket 上行已请求启动：")
                append("${AudioConstants.INPUT_SAMPLE_RATE}Hz mono ${AudioConstants.FRAME_DURATION_MS}ms")
                if (captureConfig.activityConfig.enabled) append("；本地 VAD 已启用")
                if (captureConfig.suppressUplinkDuringPlayback) append("；TTS 播放时抑制麦克风上行")
            },
        )
    }

    suspend fun stopRecordingAndAwait(maxStopLatencyMs: Long = MAX_STOP_LATENCY_MS): RealAudioStopResult {
        val stopRequestedAt = System.currentTimeMillis()
        val start = startedAtMs ?: stopRequestedAt
        val job = recordingJob
        recording = false
        runCatching { activeAudioRecord?.stop() }
        val completed = if (job != null) {
            withTimeoutOrNull(maxStopLatencyMs) {
                job.join()
                true
            } ?: false
        } else {
            true
        }
        if (!completed) job?.cancel()
        recordingJob = null

        val stoppedAt = System.currentTimeMillis()
        val frames = synchronized(encodedFramesLock) { encodedFrames.toList() }
        val latency = stoppedAt - stopRequestedAt
        val recordedMs = (stoppedAt - start).coerceAtLeast(0L)
        val rms = if (sampleCount <= 0L) 0 else sqrt(squareSum / sampleCount).toInt()
        return RealAudioStopResult(
            stopRequestedAtMs = stopRequestedAt,
            stoppedAtMs = stoppedAt,
            recordedMs = recordedMs,
            stopLatencyMs = latency,
            pcmFrames = pcmFrames,
            opusFrames = opusFrames,
            failedUploads = failedUploads,
            encodedFrames = frames,
            stoppedWithinBudget = latency <= maxStopLatencyMs && completed,
            processingSummary = processingSummary,
            errorMessage = lastError,
            peakAbs = peakAbs,
            rms = rms,
            silentRatioPercent = if (pcmFrames <= 0) 0 else silentFrames * 100 / pcmFrames,
            summary = buildString {
                append("真实音频停止：PCM $pcmFrames 帧，Opus $opusFrames 帧，上传失败 $failedUploads，停止耗时 ${latency}ms")
                append("，peak=$peakAbs, rms=$rms")
                if (!completed) append("，录音线程未在 ${maxStopLatencyMs}ms 内退出")
                lastError?.let { append("，error=$it") }
            },
        )
    }

    suspend fun playOpusFrames(opusFrames: List<ByteArray>): RealAudioPlaybackResult = withContext(Dispatchers.IO) {
        releaseDownlinkPlayback()
        opusFrames.forEach { packet -> playIncomingOpusFrameInternal(packet) }
        RealAudioPlaybackResult(
            success = downlinkPlaybackStarted,
            opusFrames = downlinkOpusFrames,
            pcmFrames = downlinkPcmFrames,
            pcmBytes = downlinkPcmBytes,
            summary = if (downlinkPlaybackStarted) {
                "真实下行播放验证完成：Opus -> PCM -> AudioTrack，PCM 帧 $downlinkPcmFrames，${downlinkPcmBytes}B"
            } else {
                "Opus 解码未产生 PCM，AudioTrack 未播放"
            },
        ).also { releaseDownlinkPlayback() }
    }

    suspend fun playIncomingOpusFrame(packet: ByteArray): RealAudioPlaybackResult = withContext(Dispatchers.IO) {
        try {
            playIncomingOpusFrameInternal(packet)
            RealAudioPlaybackResult(
                success = downlinkPlaybackStarted,
                opusFrames = downlinkOpusFrames,
                pcmFrames = downlinkPcmFrames,
                pcmBytes = downlinkPcmBytes,
                summary = if (downlinkPlaybackStarted) {
                    "真实服务端音频已解码并写入 AudioTrack：opus=$downlinkOpusFrames pcm=$downlinkPcmFrames bytes=$downlinkPcmBytes"
                } else {
                    "已接收真实服务端 Opus 帧，但暂未解出 PCM：opus=$downlinkOpusFrames"
                },
            )
        } catch (exception: Exception) {
            RealAudioPlaybackResult(
                success = false,
                opusFrames = downlinkOpusFrames,
                pcmFrames = downlinkPcmFrames,
                pcmBytes = downlinkPcmBytes,
                summary = "真实服务端音频播放失败：${exception.message ?: exception::class.java.simpleName}",
            )
        }
    }

    fun stopPlaybackAndRelease() {
        releaseDownlinkPlayback()
    }

    private fun playIncomingOpusFrameInternal(packet: ByteArray) {
        if (packet.isEmpty()) return
        synchronized(playbackLock) {
            if (downlinkDecoder == null) downlinkDecoder = AndroidOpusDecoder()
            if (downlinkPlayer == null) downlinkPlayer = AndroidAudioPlayer()
            downlinkOpusFrames += 1
            val decoded = downlinkDecoder?.decode(packet).orEmpty()
            decoded.forEach { pcm ->
                if (pcm.isEmpty()) return@forEach
                val player = downlinkPlayer ?: return@forEach
                if (!downlinkPlaybackStarted) {
                    player.play()
                    downlinkPlaybackStarted = true
                }
                player.writePcm(pcm)
                downlinkPcmFrames += 1
                downlinkPcmBytes += pcm.size
            }
        }
    }

    private fun releaseDownlinkPlayback() {
        synchronized(playbackLock) {
            runCatching { downlinkDecoder?.release() }
            runCatching { downlinkPlayer?.release() }
            downlinkDecoder = null
            downlinkPlayer = null
            downlinkPlaybackStarted = false
            downlinkPcmFrames = 0
            downlinkPcmBytes = 0L
            downlinkOpusFrames = 0
        }
    }

    fun cancel() {
        recording = false
        captureGeneration += 1L
        runCatching { activeAudioRecord?.stop() }
        recordingJob?.cancel()
        recordingJob = null
        activeAudioRecord = null
        startedAtMs = null
        releaseDownlinkPlayback()
    }

    private fun inspectLevels(bytes: ByteArray) {
        var index = 0
        var framePeak = 0
        var frameSquareSum = 0.0
        var frameSamples = 0L
        while (index + 1 < bytes.size) {
            val lo = bytes[index].toInt() and 0xFF
            val hi = bytes[index + 1].toInt()
            var sample = (hi shl 8) or lo
            if (sample > Short.MAX_VALUE) sample -= 0x10000
            framePeak = maxOf(framePeak, abs(sample))
            frameSquareSum += sample.toDouble() * sample.toDouble()
            frameSamples += 1
            index += 2
        }
        peakAbs = maxOf(peakAbs, framePeak)
        squareSum += frameSquareSum
        sampleCount += frameSamples
        if (framePeak < SILENCE_PEAK_THRESHOLD) silentFrames += 1
    }

    private companion object {
        const val MAX_STOP_LATENCY_MS = 1_500L
        const val SILENCE_PEAK_THRESHOLD = 500
    }
}

data class RealAudioStartResult(
    val started: Boolean,
    val startedAtMs: Long,
    val summary: String,
)

data class RealAudioStopResult(
    val stopRequestedAtMs: Long,
    val stoppedAtMs: Long,
    val recordedMs: Long,
    val stopLatencyMs: Long,
    val pcmFrames: Int,
    val opusFrames: Int,
    val failedUploads: Int,
    val encodedFrames: List<ByteArray>,
    val stoppedWithinBudget: Boolean,
    val processingSummary: String,
    val errorMessage: String?,
    val peakAbs: Int = 0,
    val rms: Int = 0,
    val silentRatioPercent: Int = 0,
    val summary: String,
)

data class RealAudioPlaybackResult(
    val success: Boolean,
    val opusFrames: Int,
    val pcmFrames: Int,
    val pcmBytes: Long,
    val summary: String,
)
