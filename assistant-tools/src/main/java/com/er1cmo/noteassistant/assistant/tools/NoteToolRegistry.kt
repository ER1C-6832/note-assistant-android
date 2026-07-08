package com.er1cmo.noteassistant.assistant.tools

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolRegistry
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Singleton
class NoteToolRegistry @Inject constructor(
    private val injectedTools: Set<@JvmSuppressWildcards McpTool>,
) : McpToolRegistry, McpToolExecutor {
    override val tools: List<McpTool>
        get() = injectedTools.sortedBy { it.name }

    fun listToolNames(): List<String> = tools.map { it.name }.sorted()

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
                message = "Phase4 工具尚未注册：$name",
                argumentsJson = argumentsJson,
            )
        return tool.call(argumentsJson, context).copy(toolName = tool.name)
    }
}
