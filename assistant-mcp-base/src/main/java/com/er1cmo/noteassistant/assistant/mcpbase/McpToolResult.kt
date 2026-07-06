package com.er1cmo.noteassistant.assistant.mcpbase

data class McpToolResult(
    val status: String,
    val message: String,
    val resultJson: String? = null,
)
