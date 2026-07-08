package com.er1cmo.noteassistant.assistant.runtime.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Phase3-06 fake audio engine.
 *
 * This deliberately does not touch Android AudioRecord or real Opus. Its job is to
 * prove the permission-gated push-to-talk lifecycle, fake recorder/encoder state,
 * and the release-to-stop budget before Phase3-07 migrates real audio.
 */
@Singleton
class FakeAudioEngine @Inject constructor(
    private val encoder: FakeOpusEncoder,
) {
    @Volatile
    private var recording: Boolean = false

    @Volatile
    private var startedAtMs: Long? = null

    fun isRecording(): Boolean = recording

    fun startRecording(nowMillis: Long = System.currentTimeMillis()): FakeAudioStartResult {
        recording = true
        startedAtMs = nowMillis
        val warmupFrame = encoder.encodeFakeFrame(index = 1, recordedMs = 20L)
        return FakeAudioStartResult(
            startedAtMs = nowMillis,
            pcmFrames = 1,
            opusFrames = 1,
            warmupFrame = warmupFrame,
            summary = "Fake recorder 已启动，Fake encoder 已产生 warmup 帧",
        )
    }

    suspend fun stopAndAwait(maxStopLatencyMs: Long = MAX_STOP_LATENCY_MS): FakeAudioStopResult {
        val stopRequestedAt = System.currentTimeMillis()
        val start = startedAtMs ?: stopRequestedAt
        if (!recording) {
            return FakeAudioStopResult(
                stopRequestedAtMs = stopRequestedAt,
                stoppedAtMs = stopRequestedAt,
                recordedMs = 0L,
                stopLatencyMs = 0L,
                pcmFrames = 0,
                opusFrames = 0,
                encodedFrames = emptyList(),
                stoppedWithinBudget = true,
                summary = "当前没有 Fake Audio 录音需要停止",
            )
        }

        delay(FAKE_STOP_DELAY_MS.coerceAtMost(maxStopLatencyMs))
        val stoppedAt = System.currentTimeMillis()
        val recordedMs = (stoppedAt - start).coerceAtLeast(FRAME_DURATION_MS)
        val pcmFrames = maxOf(1, (recordedMs / FRAME_DURATION_MS).toInt())
        val encodedFrames = (1..pcmFrames).map { index ->
            encoder.encodeFakeFrame(index = index, recordedMs = recordedMs)
        }
        recording = false
        startedAtMs = null
        val latency = stoppedAt - stopRequestedAt
        return FakeAudioStopResult(
            stopRequestedAtMs = stopRequestedAt,
            stoppedAtMs = stoppedAt,
            recordedMs = recordedMs,
            stopLatencyMs = latency,
            pcmFrames = pcmFrames,
            opusFrames = encodedFrames.size,
            encodedFrames = encodedFrames,
            stoppedWithinBudget = latency <= maxStopLatencyMs,
            summary = "Fake Audio 已停止：PCM $pcmFrames 帧，Fake Opus ${encodedFrames.size} 帧，停止耗时 ${latency}ms",
        )
    }

    fun cancel() {
        recording = false
        startedAtMs = null
    }

    private companion object {
        const val FRAME_DURATION_MS = 20L
        const val FAKE_STOP_DELAY_MS = 60L
        const val MAX_STOP_LATENCY_MS = 300L
    }
}

class FakeOpusEncoder @Inject constructor() {
    fun encodeFakeFrame(index: Int, recordedMs: Long): ByteArray {
        return "phase3-fake-opus:index=$index:recorded_ms=$recordedMs".toByteArray(Charsets.UTF_8)
    }
}

data class FakeAudioStartResult(
    val startedAtMs: Long,
    val pcmFrames: Int,
    val opusFrames: Int,
    val warmupFrame: ByteArray,
    val summary: String,
)

data class FakeAudioStopResult(
    val stopRequestedAtMs: Long,
    val stoppedAtMs: Long,
    val recordedMs: Long,
    val stopLatencyMs: Long,
    val pcmFrames: Int,
    val opusFrames: Int,
    val encodedFrames: List<ByteArray>,
    val stoppedWithinBudget: Boolean,
    val summary: String,
)
