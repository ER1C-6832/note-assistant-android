package com.er1cmo.noteassistant.assistant.runtime.audio

import android.media.AudioRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class RealAudioEngine @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val encodedFramesLock = Any()
    private val encodedFrames = mutableListOf<ByteArray>()

    @Volatile private var recording = false
    @Volatile private var activeAudioRecord: AudioRecord? = null
    @Volatile private var recordingJob: Job? = null
    @Volatile private var startedAtMs: Long? = null
    @Volatile private var pcmFrames = 0
    @Volatile private var opusFrames = 0
    @Volatile private var failedUploads = 0
    @Volatile private var lastError: String? = null
    @Volatile private var processingSummary: String = "尚未启动真实录音"

    fun isRecording(): Boolean = recording

    fun startRecording(
        nowMillis: Long = System.currentTimeMillis(),
        onEncodedFrame: (ByteArray) -> Boolean,
    ): RealAudioStartResult {
        if (recording) {
            return RealAudioStartResult(
                started = false,
                startedAtMs = startedAtMs ?: nowMillis,
                summary = "真实录音已经启动，忽略重复 start",
            )
        }

        synchronized(encodedFramesLock) { encodedFrames.clear() }
        recording = true
        startedAtMs = nowMillis
        pcmFrames = 0
        opusFrames = 0
        failedUploads = 0
        lastError = null
        processingSummary = "真实 AudioRecord / OpusEncoder 启动中"

        recordingJob = scope.launch {
            val recorder = AndroidAudioRecorder()
            var recordSession: AndroidAudioRecorder.RecordSession? = null
            var audioRecord: AudioRecord? = null
            var encoder: AndroidOpusEncoder? = null
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
                while (recording) {
                    val ok = recorder.readPcmFrame(audioRecord, buffer)
                    if (!ok || !recording) break
                    val frameIndex = pcmFrames
                    val pcm = buffer.copyOf()
                    val presentationTimeUs = frameIndex * AudioConstants.FRAME_DURATION_MS * 1_000L
                    pcmFrames += 1
                    val packets = encoder.encode(PcmFrame(pcm, presentationTimeUs))
                    for (packet in packets) {
                        if (packet.isEmpty()) continue
                        synchronized(encodedFramesLock) { encodedFrames += packet }
                        opusFrames += 1
                        if (!onEncodedFrame(packet)) {
                            failedUploads += 1
                        }
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
            }
        }

        return RealAudioStartResult(
            started = true,
            startedAtMs = nowMillis,
            summary = "真实 AudioRecord -> MediaCodec Opus -> WebSocket 上行已请求启动：${AudioConstants.INPUT_SAMPLE_RATE}Hz mono ${AudioConstants.FRAME_DURATION_MS}ms",
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
        if (!completed) {
            job?.cancel()
        }
        val stoppedAt = System.currentTimeMillis()
        val frames = synchronized(encodedFramesLock) { encodedFrames.toList() }
        val latency = stoppedAt - stopRequestedAt
        val recordedMs = (stoppedAt - start).coerceAtLeast(0L)
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
            summary = buildString {
                append("真实音频停止：PCM $pcmFrames 帧，Opus $opusFrames 帧，上传失败 $failedUploads，停止耗时 ${latency}ms")
                if (!completed) append("，录音线程未在 ${maxStopLatencyMs}ms 内退出")
                lastError?.let { append("，error=$it") }
            },
        )
    }

    suspend fun playOpusFrames(opusFrames: List<ByteArray>): RealAudioPlaybackResult = withContext(Dispatchers.IO) {
        if (opusFrames.isEmpty()) {
            return@withContext RealAudioPlaybackResult(
                success = false,
                opusFrames = 0,
                pcmFrames = 0,
                pcmBytes = 0L,
                summary = "没有可播放的 Opus 帧；请确认设备编码器是否产出数据",
            )
        }
        var decoder: AndroidOpusDecoder? = null
        var player: AndroidAudioPlayer? = null
        var pcmFrames = 0
        var pcmBytes = 0L
        var playbackStarted = false
        try {
            decoder = AndroidOpusDecoder()
            player = AndroidAudioPlayer()
            opusFrames.take(MAX_LOOPBACK_PLAYBACK_FRAMES).forEach { packet ->
                val decoded = decoder.decode(packet)
                decoded.forEach { pcm ->
                    if (pcm.isEmpty()) return@forEach
                    if (!playbackStarted) {
                        player.play()
                        playbackStarted = true
                    }
                    player.writePcm(pcm)
                    pcmFrames += 1
                    pcmBytes += pcm.size
                }
            }
            decoder.release().forEach { pcm ->
                if (pcm.isNotEmpty()) {
                    if (!playbackStarted) {
                        player.play()
                        playbackStarted = true
                    }
                    player.writePcm(pcm)
                    pcmFrames += 1
                    pcmBytes += pcm.size
                }
            }
            RealAudioPlaybackResult(
                success = playbackStarted,
                opusFrames = opusFrames.size.coerceAtMost(MAX_LOOPBACK_PLAYBACK_FRAMES),
                pcmFrames = pcmFrames,
                pcmBytes = pcmBytes,
                summary = if (playbackStarted) {
                    "真实下行播放验证完成：Opus -> PCM -> AudioTrack，PCM 帧 $pcmFrames，${pcmBytes}B"
                } else {
                    "Opus 解码未产生 PCM，AudioTrack 未播放"
                },
            )
        } catch (exception: Exception) {
            RealAudioPlaybackResult(
                success = false,
                opusFrames = opusFrames.size.coerceAtMost(MAX_LOOPBACK_PLAYBACK_FRAMES),
                pcmFrames = pcmFrames,
                pcmBytes = pcmBytes,
                summary = "真实下行播放失败：${exception.message ?: exception::class.java.simpleName}",
            )
        } finally {
            runCatching { player?.release() }
            runCatching { decoder?.release() }
        }
    }

    fun cancel() {
        recording = false
        runCatching { activeAudioRecord?.stop() }
        recordingJob?.cancel()
        recordingJob = null
        activeAudioRecord = null
        startedAtMs = null
    }

    private companion object {
        const val MAX_STOP_LATENCY_MS = 300L
        const val MAX_LOOPBACK_PLAYBACK_FRAMES = 24
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
    val summary: String,
)

data class RealAudioPlaybackResult(
    val success: Boolean,
    val opusFrames: Int,
    val pcmFrames: Int,
    val pcmBytes: Long,
    val summary: String,
)
