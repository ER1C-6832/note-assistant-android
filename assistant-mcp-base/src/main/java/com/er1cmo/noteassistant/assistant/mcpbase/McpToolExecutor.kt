package com.er1cmo.noteassistant.assistant.mcpbase

interface McpToolExecutor {
    fun listDescriptors(): List<McpToolDescriptor>
    fun findTool(name: String): McpTool?
    suspend fun execute(
        name: String,
        argumentsJson: String,
        context: McpToolContext = McpToolContext(),
    ): McpToolResult
}

class FailClosedMcpToolExecutor : McpToolExecutor {
    override fun listDescriptors(): List<McpToolDescriptor> = emptyList()

    override fun findTool(name: String): McpTool? = null

    override suspend fun execute(
        name: String,
        argumentsJson: String,
        context: McpToolContext,
    ): McpToolResult = McpToolResult.blocked(
        toolName = name,
        message = "MCP tool executor 尚未注入；Phase4 fail-closed，未修改任何便签。",
        argumentsJson = argumentsJson,
        errorCode = McpToolResult.ERROR_EXECUTOR_UNAVAILABLE,
    )
}
