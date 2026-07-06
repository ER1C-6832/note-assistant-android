package com.er1cmo.noteassistant.assistant.mcpbase

class McpToolRegistry(
    private val tools: Set<McpTool>,
) {
    fun listTools(): List<McpTool> = tools.sortedBy { it.name }
    fun findTool(name: String): McpTool? = tools.firstOrNull { it.name == name }
}
