package com.er1cmo.noteassistant.assistant.runtime.context

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantTurnContextStoreTest {
    @Test
    fun wakeWordSessionMapsEveryToolCallToWakeword() {
        val store = AssistantTurnContextStore()
        store.beginSession(AssistantEntrySource.WakeWord, "wake-session", "小智")

        assertEquals(McpToolContext.SOURCE_WAKEWORD, store.currentMcpSource())
        assertEquals("wake-session", store.current()?.conversationId)
    }

    @Test
    fun laterPushToTalkOverwritesWakewordAndClearRemovesSource() {
        val store = AssistantTurnContextStore()
        store.beginSession(AssistantEntrySource.WakeWord, "wake-session", "小智")
        store.beginSession(AssistantEntrySource.PushToTalk, "ptt-session")

        assertEquals(McpToolContext.SOURCE_VOICE, store.currentMcpSource())
        store.clear("ptt-session")
        assertNull(store.current())
        assertEquals(McpToolContext.SOURCE_VOICE, store.currentMcpSource())
    }

    @Test
    fun staleClearCannotRemoveNewerSession() {
        val store = AssistantTurnContextStore()
        store.beginSession(AssistantEntrySource.WakeWord, "old")
        store.beginSession(AssistantEntrySource.StreamingButton, "new")

        store.clear("old")

        assertEquals("new", store.current()?.conversationId)
    }
}
