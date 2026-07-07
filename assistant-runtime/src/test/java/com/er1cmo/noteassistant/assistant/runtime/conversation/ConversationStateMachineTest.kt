package com.er1cmo.noteassistant.assistant.runtime.conversation

import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConversationStateMachineTest {
    private val machine = ConversationStateMachine()

    @Test
    fun defaultStateIsNotConnected() {
        val state = AssistantState()
        assertNotEquals(AssistantPhase.Connected, state.phase)
        assertEquals(AssistantConnectionStatus.Disconnected, state.connection)
    }

    @Test
    fun enabledRuntimeCanConnectAndSendText() {
        val enabled = machine.enable(AssistantState.disabled(), nowMillis = 1L)
        val connecting = machine.connecting(enabled, nowMillis = 2L)
        val connected = machine.connected(connecting, sessionId = "s1", nowMillis = 3L)
        val thinking = machine.textSubmitted(connected, text = "hello", nowMillis = 4L)
        val replied = machine.assistantText(thinking, text = "world", nowMillis = 5L)

        assertEquals(AssistantPhase.Connected, connected.phase)
        assertEquals(AssistantPhase.Thinking, thinking.phase)
        assertEquals("hello", thinking.lastUserText)
        assertEquals(AssistantPhase.Connected, replied.phase)
        assertEquals("world", replied.lastAssistantText)
    }

    @Test
    fun disabledRuntimeRejectsRecording() {
        val result = machine.startListening(AssistantState.disabled(), nowMillis = 1L)
        assertEquals(AssistantPhase.Error, result.phase)
    }
}
