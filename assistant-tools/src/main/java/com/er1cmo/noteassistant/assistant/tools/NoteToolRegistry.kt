package com.er1cmo.noteassistant.assistant.tools

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Singleton
class NoteToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards McpTool>,
) {
    fun listToolNames(): List<String> = tools.map { it.name }.sorted()
}
