package com.er1cmo.noteassistant.assistant.runtime.audio

import com.er1cmo.noteassistant.assistant.runtime.state.VoiceActivityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorBargeInTest {
    @Test
    fun short_noise_pulse_does_not_trigger_barge_in() {
        val detector = VoiceActivityDetector(VoiceActivityConfig.bargeInMonitoring())
        repeat(34) { detector.processPcm16(frame(0)) }

        val events = buildList {
            repeat(4) { detector.processPcm16(frame(4_000))?.let(::add) }
            repeat(10) { detector.processPcm16(frame(0))?.let(::add) }
        }

        assertFalse(events.any { it.state == VoiceActivityState.SpeechDetected })
    }

    @Test
    fun sustained_user_speech_triggers_then_ends_after_trailing_silence() {
        val detector = VoiceActivityDetector(VoiceActivityConfig.bargeInMonitoring())
        repeat(34) { detector.processPcm16(frame(0)) }

        val events = buildList {
            repeat(10) { detector.processPcm16(frame(4_000))?.let(::add) }
            repeat(70) { detector.processPcm16(frame(0))?.let(::add) }
        }

        assertTrue(events.any { it.state == VoiceActivityState.SpeechDetected })
        assertTrue(events.any { it.state == VoiceActivityState.EndOfSpeech })
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
