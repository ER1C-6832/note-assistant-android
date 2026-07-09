package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult

abstract class AbstractPhase4ExtendedCommandTool(
    private val commandService: Phase4ExtendedCommandService,
) : McpTool {
    final override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val result = commandService.execute(
            toolName = name,
            argumentsJson = argumentsJson,
            source = context.toCommandSource(),
        )
        return result.toMcpToolResult(toolName = name, argumentsJson = argumentsJson)
    }
}
