package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import org.json.JSONObject

/**
 * Shared base for low-risk UI surface tools.
 *
 * Keep this file free of concrete `UiShow*Tool` classes. Each concrete tool lives
 * in its own file and is bound exactly once in AssistantToolsModule. Keeping this
 * file abstract avoids Kotlin redeclaration errors when full overlay zips are
 * applied repeatedly during Phase4 Gate D fixes.
 */
abstract class AbstractUiSurfaceTool(
    protected val uiCommandBus: UiCommandBus,
) : McpTool {
    abstract override val name: String
    abstract override val description: String

    protected open val inputSchemaJson: String = "{\"type\":\"object\",\"additionalProperties\":true}"
    protected abstract fun buildCommand(parser: ToolArgumentParser): UiCommand
    protected abstract fun resultPayload(parser: ToolArgumentParser): JSONObject

    final override val riskLevel: McpRiskLevel = McpRiskLevel.Low

    final override val descriptor: McpToolDescriptor
        get() = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = inputSchemaJson,
            riskLevel = McpRiskLevel.Low,
            mutates = false,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
            examples = listOf(description),
        )

    final override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    final override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "$name 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val command = buildCommand(parser)
        uiCommandBus.emit(command)
        return McpToolResult.success(
            message = description,
            resultJson = resultPayload(parser)
                .put("shown", true)
                .put("tool", name)
                .put("ui_command", command.javaClass.simpleName)
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
        )
    }
}
