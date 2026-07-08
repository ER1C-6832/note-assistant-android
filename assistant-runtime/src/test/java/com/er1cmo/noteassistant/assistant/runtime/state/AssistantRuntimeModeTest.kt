package com.er1cmo.noteassistant.assistant.runtime.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRuntimeModeTest {
    @Test
    fun defaultRuntimeModeIsFakeAndGateBFlagsAreFalse() {
        val state = AssistantState.idle(nowMillis = 1L)

        assertTrue(state.fakeRuntime)
        assertFalse(state.isRealRuntime)
        assertFalse(state.gateBRealHandshakeVerified)
        assertFalse(state.gateBRealTextVerified)
        assertFalse(state.gateBRealAudioUploadVerified)
        assertFalse(state.gateBRealAudioPlaybackVerified)
        assertFalse(state.gateBRealToolCallBlockedVerified)
    }

    @Test
    fun realRuntimeModeCanTrackGateBProgress() {
        val state = AssistantState.idle(nowMillis = 1L).copy(
            runtimeMode = AssistantRuntimeMode.Real,
            fakeRuntime = false,
            gateBRealHandshakeVerified = true,
            gateBRealTextVerified = true,
        )

        assertTrue(state.isRealRuntime)
        assertTrue(state.gateBRealHandshakeVerified)
        assertTrue(state.gateBRealTextVerified)
    }
}
