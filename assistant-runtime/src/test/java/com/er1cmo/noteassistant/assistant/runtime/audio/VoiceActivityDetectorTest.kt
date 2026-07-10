package com.er1cmo.noteassistant.assistant.runtime.audio

import com.er1cmo.noteassistant.assistant.runtime.state.VoiceActivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {
    @Test
    fun speech_then_trailing_silence_emits_end_of_speech_once() {
        val detector = VoiceActivityDetector(
            VoiceActivityConfig(
                warmupMs = 40L,
                minSpeechFrames = 2,
                trailingSilenceMs = 60L,
                noSpeechTimeoutMs = 1_000L,
            ),
        )

        repeat(2) { detector.processPcm16(frame(amplitude = 0)) }
        repeat(3) { detector.processPcm16(frame(amplitude = 4_000)) }
        val events = buildList {
            repeat(5) {
                detector.processPcm16(frame(amplitude = 0))?.let(::add)
            }
        }

        assertTrue(events.any { it.state == VoiceActivityState.EndOfSpeech })
        assertEquals(null, detector.processPcm16(frame(amplitude = 0)))
    }

    @Test
    fun silence_only_emits_no_speech_timeout() {
        val detector = VoiceActivityDetector(
            VoiceActivityConfig(
                warmupMs = 20L,
                minSpeechFrames = 2,
                trailingSilenceMs = 60L,
                noSpeechTimeoutMs = 100L,
            ),
        )

        val events = buildList {
            repeat(8) {
                detector.processPcm16(frame(amplitude = 0))?.let(::add)
            }
        }

        assertTrue(events.any { it.state == VoiceActivityState.NoSpeechTimeout })
    }

    private fun frame(amplitude: Int): ByteArray {
        val bytes = ByteArray(320 * 2)
        repeat(320) { index ->
            val sample = if (index % 2 == 0) amplitude else -amplitude
            bytes[index * 2] = (sample and 0xFF).toByte()
            bytes[index * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
