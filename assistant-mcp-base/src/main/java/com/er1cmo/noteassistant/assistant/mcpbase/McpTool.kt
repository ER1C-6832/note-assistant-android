package com.er1cmo.noteassistant.assistant.mcpbase

interface McpTool {
    val name: String
    val description: String
    val riskLevel: McpRiskLevel

    val descriptor: McpToolDescriptor
        get() = McpToolDescriptor(
            name = name,
            description = description,
            riskLevel = riskLevel,
            mutates = riskLevel != McpRiskLevel.Low,
            confirmation = if (riskLevel == McpRiskLevel.High) {
                McpToolDescriptor.CONFIRMATION_REQUIRED
            } else {
                McpToolDescriptor.CONFIRMATION_NOT_REQUIRED_BY_DEFAULT
            },
        )

    suspend fun call(argumentsJson: String): McpToolResult

    suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult = call(argumentsJson)
}
