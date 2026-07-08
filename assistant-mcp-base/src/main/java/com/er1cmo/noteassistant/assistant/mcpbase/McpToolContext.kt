package com.er1cmo.noteassistant.assistant.mcpbase

data class McpToolContext(
    val source: String = SOURCE_VOICE,
    val runtimeMode: String? = null,
    val conversationId: String? = null,
    val sessionId: String? = null,
    val requestIdJson: String? = null,
) {
    companion object {
        const val SOURCE_VOICE = "voice"
        const val SOURCE_LOCAL_TOOL_SIMULATOR = "local_tool_simulator"
        const val SOURCE_WAKEWORD = "wakeword"
    }
}
