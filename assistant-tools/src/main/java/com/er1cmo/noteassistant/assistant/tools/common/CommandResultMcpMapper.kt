package com.er1cmo.noteassistant.assistant.tools.common

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.notes.domain.command.CommandErrorCode
import com.er1cmo.noteassistant.notes.domain.command.CommandResult
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.command.RiskLevel

fun McpToolContext.toCommandSource(): CommandSource = when (source) {
    McpToolContext.SOURCE_LOCAL_TOOL_SIMULATOR -> CommandSource.LocalToolSimulator
    McpToolContext.SOURCE_WAKEWORD -> CommandSource.Wakeword
    McpToolContext.SOURCE_VOICE -> CommandSource.Voice
    else -> CommandSource.Voice
}

fun CommandResult.toMcpToolResult(
    toolName: String,
    argumentsJson: String,
): McpToolResult {
    val mappedRisk = riskLevel.toMcpRiskLevel()
    return when (status) {
        CommandStatus.Success -> McpToolResult.success(
            message = message,
            resultJson = resultJson,
            toolName = toolName,
            risk = mappedRisk,
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
        )
        CommandStatus.PartialSuccess -> McpToolResult(
            status = McpToolStatus.PartialSuccess.storageValue,
            message = message,
            resultJson = resultJson,
            risk = mappedRisk,
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            errorCode = errorCode?.storageValue,
            toolName = toolName,
            argumentsJson = argumentsJson,
        )
        CommandStatus.RequiresConfirmation -> {
            val id = confirmationId
            if (id.isNullOrBlank()) {
                McpToolResult.blocked(
                    toolName = toolName,
                    message = "需要确认，但没有生成 confirmation_id；已阻断执行。",
                    argumentsJson = argumentsJson,
                    resultJson = resultJson,
                    errorCode = CommandErrorCode.RequiresConfirmation.storageValue,
                )
            } else {
                McpToolResult.requiresConfirmation(
                    message = message,
                    confirmationId = id,
                    resultJson = resultJson,
                    toolName = toolName,
                    commandLogId = commandLogId,
                    affectedNoteIds = affectedNoteIds,
                    affectedTagIds = affectedTagIds,
                    summary = message,
                    previewJson = resultJson,
                )
            }
        }
        CommandStatus.Blocked -> McpToolResult.blocked(
            toolName = toolName,
            message = message,
            argumentsJson = argumentsJson,
            resultJson = resultJson,
            errorCode = errorCode?.storageValue ?: "blocked",
        )
        CommandStatus.Failed -> McpToolResult.failed(
            message = message,
            toolName = toolName,
            argumentsJson = argumentsJson,
            errorCode = errorCode?.storageValue ?: McpToolResult.ERROR_VALIDATION,
            risk = mappedRisk,
        ).copy(
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            resultJson = resultJson,
        )
    }
}

fun RiskLevel.toMcpRiskLevel(): McpRiskLevel = when (this) {
    RiskLevel.Low -> McpRiskLevel.Low
    RiskLevel.Medium -> McpRiskLevel.Medium
    RiskLevel.High -> McpRiskLevel.High
}
