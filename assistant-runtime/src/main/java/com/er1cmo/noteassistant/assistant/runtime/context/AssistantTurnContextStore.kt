package com.er1cmo.noteassistant.assistant.runtime.context

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveAssistantTurnContext(
    val entrySource: AssistantEntrySource,
    val conversationId: String,
    val wakeKeyword: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
)

@Singleton
class AssistantTurnContextStore @Inject constructor() {
    private val active = AtomicReference<ActiveAssistantTurnContext?>(null)

    fun beginSession(
        entrySource: AssistantEntrySource,
        conversationId: String,
        wakeKeyword: String? = null,
    ): ActiveAssistantTurnContext {
        val context = ActiveAssistantTurnContext(
            entrySource = entrySource,
            conversationId = conversationId,
            wakeKeyword = wakeKeyword,
        )
        active.set(context)
        return context
    }

    fun current(): ActiveAssistantTurnContext? = active.get()

    fun currentMcpSource(): String = when (active.get()?.entrySource) {
        AssistantEntrySource.WakeWord -> McpToolContext.SOURCE_WAKEWORD
        else -> McpToolContext.SOURCE_VOICE
    }

    fun clear(expectedConversationId: String? = null) {
        if (expectedConversationId == null) {
            active.set(null)
            return
        }
        while (true) {
            val current = active.get() ?: return
            if (current.conversationId != expectedConversationId) return
            if (active.compareAndSet(current, null)) return
        }
    }
}
