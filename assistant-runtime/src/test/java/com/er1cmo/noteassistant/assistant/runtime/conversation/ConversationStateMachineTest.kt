package com.er1cmo.noteassistant.assistant.runtime.conversation

import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStateMachineTest {
    private val machine = ConversationStateMachine()

    @Test
    fun enableMovesDisabledRuntimeToIdle() {
        val state = machine.enable(AssistantState.disabled(), nowMillis = 1L)

        assertTrue(state.assistantEnabled)
        assertEquals(AssistantPhase.Idle, state.phase)
        assertEquals(AssistantConnectionStatus.Disconnected, state.connection)
        assertNull(state.errorMessage)
    }

    @Test
    fun connectingWhileDisabledProducesError() {
        val state = machine.connecting(AssistantState.disabled(), nowMillis = 1L)

        assertEquals(AssistantPhase.Error, state.phase)
        assertEquals("助手未启用，不能连接", state.errorMessage)
        assertEquals(1, state.runtimeErrorCount)
    }

    @Test
    fun textSubmissionRequiresConnectedState() {
        val enabled = machine.enable(AssistantState.disabled(), nowMillis = 1L)
        val state = machine.textSubmitted(enabled, "hello", nowMillis = 2L)

        assertEquals(AssistantPhase.Error, state.phase)
        assertEquals("助手未连接，不能发送文本", state.errorMessage)
    }

    @Test
    fun pttMovesThroughListeningAndThinking() {
        val connected = machine.connected(
            current = machine.enable(AssistantState.disabled(), nowMillis = 1L),
            sessionId = "session-1",
            nowMillis = 2L,
        )

        val listening = machine.startListening(connected, nowMillis = 3L)
        assertEquals(AssistantPhase.Listening, listening.phase)
        assertEquals(AssistantAudioStatus.Recording, listening.audio)

        val stopped = machine.stopListening(listening, nowMillis = 4L)
        assertEquals(AssistantPhase.Thinking, stopped.phase)
        assertEquals(AssistantAudioStatus.Idle, stopped.audio)
    }

    @Test
    fun reconnectingStateIsExplicitAndTestable() {
        val connected = machine.connected(
            current = machine.enable(AssistantState.disabled(), nowMillis = 1L),
            sessionId = "session-1",
            nowMillis = 2L,
        )

        val reconnecting = machine.reconnecting(connected, reason = "1006", attempt = 1, nowMillis = 3L)

        assertEquals(AssistantPhase.Reconnecting, reconnecting.phase)
        assertEquals(AssistantConnectionStatus.Connecting, reconnecting.connection)
        assertEquals(1, reconnecting.reconnectAttempt)
        assertFalse(reconnecting.isConnected)
    }
}
