package com.er1cmo.noteassistant.assistant.runtime.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConstantsTest {
    @Test
    fun inputFrameIsTwentyMsMonoPcm16() {
        assertEquals(16_000, AudioConstants.INPUT_SAMPLE_RATE)
        assertEquals(20, AudioConstants.FRAME_DURATION_MS)
        assertEquals(320, AudioConstants.SAMPLES_PER_FRAME)
        assertEquals(640, AudioConstants.PCM_FRAME_BYTES)
    }

    @Test
    fun outputTrackUsesDecoderAlignedRate() {
        assertEquals(48_000, AudioConstants.OUTPUT_SAMPLE_RATE)
        assertEquals(960, AudioConstants.OUTPUT_SAMPLES_PER_FRAME)
        assertEquals(1_920, AudioConstants.OUTPUT_PCM_FRAME_BYTES)
    }
}
