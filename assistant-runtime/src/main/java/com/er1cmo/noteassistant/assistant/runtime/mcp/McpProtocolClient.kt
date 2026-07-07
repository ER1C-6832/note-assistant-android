package com.er1cmo.noteassistant.assistant.runtime.mcp

import javax.inject.Inject

class McpProtocolClient @Inject constructor() {
    fun toolsList(): List<McpToolDescriptor> = listOf(
        McpToolDescriptor(
            name = "phase3.echo",
            description = "Phase3 fake runtime echo tool. Does not read or mutate notes.",
            readOnly = true,
        ),
        McpToolDescriptor(
            name = "phase3.status",
            description = "Returns assistant runtime status metadata only.",
            readOnly = true,
        ),
    )

    fun handleToolCall(toolName: String, argumentsJson: String): McpToolResult {
        val normalized = toolName.trim().lowercase()
        return when {
            normalized.startsWith("notes.") || normalized.startsWith("tags.") -> McpToolResult.blocked(
                toolName = toolName,
                message = "Phase3 已收到工具调用，但便签工具执行被阻断；真实执行从 Phase4 开始并必须走 NoteCommandService。",
                argumentsJson = argumentsJson,
            )
            normalized == "phase3.echo" -> McpToolResult.success(
                toolName = toolName,
                message = "Phase3 echo ok",
                resultJson = "{\"echo\":$argumentsJson}",
            )
            normalized == "phase3.status" -> McpToolResult.success(
                toolName = toolName,
                message = "Phase3 runtime status tool is available",
                resultJson = "{\"phase3\":true,\"note_mutation_enabled\":false}",
            )
            else -> McpToolResult.blocked(
                toolName = toolName,
                message = "Phase3 暂不支持该工具：$toolName",
                argumentsJson = argumentsJson,
            )
        }
    }
}

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val readOnly: Boolean,
)

data class McpToolResult(
    val toolName: String,
    val status: McpToolStatus,
    val message: String,
    val resultJson: String? = null,
    val argumentsJson: String? = null,
) {
    companion object {
        fun success(toolName: String, message: String, resultJson: String): McpToolResult = McpToolResult(
            toolName = toolName,
            status = McpToolStatus.Success,
            message = message,
            resultJson = resultJson,
        )

        fun blocked(toolName: String, message: String, argumentsJson: String): McpToolResult = McpToolResult(
            toolName = toolName,
            status = McpToolStatus.Blocked,
            message = message,
            argumentsJson = argumentsJson,
            resultJson = "{\"blocked\":true,\"phase\":\"phase3\",\"note_mutation_enabled\":false}",
        )
    }
}

enum class McpToolStatus {
    Success,
    Blocked,
}
