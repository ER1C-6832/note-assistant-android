package com.er1cmo.noteassistant.assistant.runtime.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAudioEngineTest {
    @Test
    fun startRecordingMarksRecordingAndProducesWarmupFrame() {
        val engine = FakeAudioEngine(FakeOpusEncoder())

        val result = engine.startRecording(nowMillis = 1_000L)

        assertTrue(engine.isRecording())
        assertTrue(result.pcmFrames > 0)
        assertTrue(result.opusFrames > 0)
        assertTrue(result.warmupFrame.isNotEmpty())
    }

    @Test
    fun stopAndAwaitStopsWithinThreeHundredMilliseconds() = runBlocking {
        val engine = FakeAudioEngine(FakeOpusEncoder())
        engine.startRecording(nowMillis = System.currentTimeMillis() - 120L)

        val result = engine.stopAndAwait(maxStopLatencyMs = 300L)

        assertFalse(engine.isRecording())
        assertTrue(result.stoppedWithinBudget)
        assertTrue(result.stopLatencyMs <= 300L)
        assertTrue(result.pcmFrames > 0)
        assertTrue(result.opusFrames > 0)
        assertTrue(result.encodedFrames.isNotEmpty())
    }
}
