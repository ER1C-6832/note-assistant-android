package com.er1cmo.noteassistant.assistant.mcpbase

interface McpTool {
    val name: String
    val description: String
    val riskLevel: McpRiskLevel
    suspend fun call(argumentsJson: String): McpToolResult
}
