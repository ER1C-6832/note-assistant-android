package com.er1cmo.noteassistant.assistant.mcpbase

interface McpToolRegistry : McpToolExecutor {
    val tools: List<McpTool>

    override fun listDescriptors(): List<McpToolDescriptor> = tools
        .map { it.descriptor }
        .sortedBy { it.name }

    override fun findTool(name: String): McpTool? = tools.firstOrNull { it.name == name }

    override suspend fun execute(
        name: String,
        argumentsJson: String,
        context: McpToolContext,
    ): McpToolResult {
        val tool = findTool(name)
            ?: return McpToolResult.notImplemented(
                toolName = name,
                message = "暂不支持该工具：$name",
                argumentsJson = argumentsJson,
            )
        return tool.call(argumentsJson, context).copy(toolName = tool.name)
    }
}
