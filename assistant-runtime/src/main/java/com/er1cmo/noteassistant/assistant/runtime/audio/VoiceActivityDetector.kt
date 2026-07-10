package com.er1cmo.noteassistant.assistant.runtime.audio

import com.er1cmo.noteassistant.assistant.runtime.state.VoiceActivityState
import kotlin.math.abs
import kotlin.math.sqrt

data class VoiceActivityConfig(
    val enabled: Boolean = false,
    val speechPeakThreshold: Int = 900,
    val speechRmsThreshold: Int = 180,
    val silencePeakThreshold: Int = 1_050,
    val silenceRmsThreshold: Int = 260,
    val minSpeechFrames: Int = 3,
    val trailingSilenceMs: Long = 900L,
    val warmupMs: Long = 160L,
    val noSpeechTimeoutMs: Long = 15_000L,
) {
    companion object {
        val Disabled = VoiceActivityConfig(enabled = false)
        fun streaming(noSpeechTimeoutMs: Long): VoiceActivityConfig = VoiceActivityConfig(
            enabled = true,
            noSpeechTimeoutMs = noSpeechTimeoutMs,
        )
    }
}

data class VoiceActivitySnapshot(
    val state: VoiceActivityState,
    val peakAbs: Int,
    val rms: Int,
    val elapsedMs: Long,
    val speechSeen: Boolean,
)

class VoiceActivityDetector(
    private val config: VoiceActivityConfig,
    private val frameDurationMs: Long = AudioConstants.FRAME_DURATION_MS.toLong(),
) {
    private var frameCount = 0
    private var speechFrames = 0
    private var trailingSilentFrames = 0
    private var speechSeen = false
    private var noisePeakEma = 0f
    private var noiseRmsEma = 0f
    private var terminalState: VoiceActivityState? = null
    private var lastReportedState = VoiceActivityState.Disabled

    fun processPcm16(bytes: ByteArray): VoiceActivitySnapshot? {
        if (!config.enabled || terminalState != null) return null

        frameCount += 1
        val level = inspectPcm16(bytes)
        val elapsedMs = frameCount * frameDurationMs
        val warmupFrames = config.warmupMs.toFrames()
        val trailingSilenceFrames = config.trailingSilenceMs.toFrames()
        val noSpeechTimeoutFrames = config.noSpeechTimeoutMs.toFrames()

        fun updateNoiseFloor() {
            if (noisePeakEma <= 0f) {
                noisePeakEma = level.peakAbs.toFloat()
                noiseRmsEma = level.rms.toFloat()
            } else {
                noisePeakEma = noisePeakEma * NOISE_EMA_DECAY + level.peakAbs * (1f - NOISE_EMA_DECAY)
                noiseRmsEma = noiseRmsEma * NOISE_EMA_DECAY + level.rms * (1f - NOISE_EMA_DECAY)
            }
        }

        if (frameCount < warmupFrames) {
            updateNoiseFloor()
            return snapshotIfChanged(VoiceActivityState.Warmup, level, elapsedMs)
        }

        val dynamicSpeechPeak = maxOf(config.speechPeakThreshold, (noisePeakEma * 2.8f).toInt() + 320)
        val dynamicSpeechRms = maxOf(config.speechRmsThreshold, (noiseRmsEma * 2.3f).toInt() + 70)
        val dynamicSilencePeak = maxOf(config.silencePeakThreshold, (noisePeakEma * 2.15f).toInt() + 260)
        val dynamicSilenceRms = maxOf(config.silenceRmsThreshold, (noiseRmsEma * 1.9f).toInt() + 60)

        val isSpeech = level.peakAbs >= dynamicSpeechPeak || level.rms >= dynamicSpeechRms
        val isQuiet = level.peakAbs <= dynamicSilencePeak && level.rms <= dynamicSilenceRms

        if (!speechSeen) {
            if (!isSpeech) updateNoiseFloor()
            speechFrames = if (isSpeech) speechFrames + 1 else 0
            if (speechFrames >= config.minSpeechFrames) {
                speechSeen = true
                trailingSilentFrames = 0
                return snapshotIfChanged(VoiceActivityState.SpeechDetected, level, elapsedMs, force = true)
            }
            if (frameCount >= noSpeechTimeoutFrames) {
                terminalState = VoiceActivityState.NoSpeechTimeout
                return snapshotIfChanged(VoiceActivityState.NoSpeechTimeout, level, elapsedMs, force = true)
            }
            return snapshotIfChanged(VoiceActivityState.WaitingForSpeech, level, elapsedMs)
        }

        if (isSpeech) {
            trailingSilentFrames = 0
            return snapshotIfChanged(VoiceActivityState.SpeechActive, level, elapsedMs)
        }

        trailingSilentFrames += if (isQuiet) 1 else 1
        if (trailingSilentFrames >= trailingSilenceFrames) {
            terminalState = VoiceActivityState.EndOfSpeech
            return snapshotIfChanged(VoiceActivityState.EndOfSpeech, level, elapsedMs, force = true)
        }
        return null
    }

    private fun snapshotIfChanged(
        state: VoiceActivityState,
        level: FrameLevel,
        elapsedMs: Long,
        force: Boolean = false,
    ): VoiceActivitySnapshot? {
        if (!force && state == lastReportedState) return null
        lastReportedState = state
        return VoiceActivitySnapshot(
            state = state,
            peakAbs = level.peakAbs,
            rms = level.rms,
            elapsedMs = elapsedMs,
            speechSeen = speechSeen,
        )
    }

    private fun Long.toFrames(): Int =
        ((this + frameDurationMs - 1L) / frameDurationMs).toInt().coerceAtLeast(1)

    private fun inspectPcm16(bytes: ByteArray): FrameLevel {
        var peak = 0
        var squareSum = 0.0
        var sampleCount = 0L
        var index = 0
        while (index + 1 < bytes.size) {
            val lo = bytes[index].toInt() and 0xFF
            val hi = bytes[index + 1].toInt()
            var sample = (hi shl 8) or lo
            if (sample > Short.MAX_VALUE) sample -= 0x10000
            peak = maxOf(peak, abs(sample))
            squareSum += sample.toDouble() * sample.toDouble()
            sampleCount += 1
            index += 2
        }
        val rms = if (sampleCount <= 0L) 0 else sqrt(squareSum / sampleCount).toInt()
        return FrameLevel(peakAbs = peak, rms = rms)
    }

    private data class FrameLevel(val peakAbs: Int, val rms: Int)

    private companion object {
        const val NOISE_EMA_DECAY = 0.93f
    }
}
