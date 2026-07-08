package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONArray
import org.json.JSONObject

data class McpToolResult(
    val status: String,
    val message: String,
    val resultJson: String? = null,
    val risk: McpRiskLevel = McpRiskLevel.Low,
    val requiresConfirmation: Boolean = false,
    val confirmationId: String? = null,
    val confirmationSummary: String? = null,
    val confirmationPreviewJson: String? = null,
    val expiresAt: Long? = null,
    val commandLogId: Long? = null,
    val affectedNoteIds: List<Long> = emptyList(),
    val affectedTagIds: List<Long> = emptyList(),
    val errorCode: String? = null,
    val toolName: String? = null,
    val argumentsJson: String? = null,
) {
    val statusEnum: McpToolStatus
        get() = McpToolStatus.fromStorage(status)

    fun toEnvelopeJsonObject(): JSONObject {
        val envelope = JSONObject()
            .put("status", status)
            .put("message", message)
            .put("risk", risk.storageValue)
            .put("requires_confirmation", requiresConfirmation)
            .put("confirmation_id", confirmationId ?: JSONObject.NULL)
            .put("command_log_id", commandLogId ?: JSONObject.NULL)
            .put("affected_note_ids", JSONArray(affectedNoteIds))
            .put("affected_tag_ids", JSONArray(affectedTagIds))
            .put("result", resultJson?.toJsonObjectOrArrayOrString() ?: JSONObject())
        toolName?.let { envelope.put("tool_name", it) }
        argumentsJson?.let { envelope.put("arguments", it.toJsonObjectOrArrayOrString()) }
        errorCode?.let { envelope.put("error_code", it) }
        confirmationSummary?.let { envelope.put("confirmation_summary", it) }
        confirmationPreviewJson?.let { envelope.put("confirmation_preview", it.toJsonObjectOrArrayOrString()) }
        expiresAt?.let { envelope.put("expires_at", it) }
        return envelope
    }

    companion object {
        fun success(
            message: String,
            resultJson: String? = null,
            toolName: String? = null,
            risk: McpRiskLevel = McpRiskLevel.Low,
            commandLogId: Long? = null,
            affectedNoteIds: List<Long> = emptyList(),
            affectedTagIds: List<Long> = emptyList(),
        ): McpToolResult = McpToolResult(
            status = McpToolStatus.Success.storageValue,
            message = message,
            resultJson = resultJson,
            risk = risk,
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            toolName = toolName,
        )

        fun failed(
            message: String,
            toolName: String? = null,
            argumentsJson: String? = null,
            errorCode: String? = ERROR_VALIDATION,
            risk: McpRiskLevel = McpRiskLevel.High,
        ): McpToolResult = McpToolResult(
            status = McpToolStatus.Failed.storageValue,
            message = message,
            risk = risk,
            errorCode = errorCode,
            toolName = toolName,
            argumentsJson = argumentsJson,
        )

        fun invalidJson(
            toolName: String? = null,
            argumentsJson: String? = null,
            message: String = "参数不是有效 JSON",
        ): McpToolResult = failed(
            message = message,
            toolName = toolName,
            argumentsJson = argumentsJson,
            errorCode = ERROR_INVALID_JSON,
            risk = McpRiskLevel.High,
        )

        fun blocked(
            toolName: String,
            message: String,
            argumentsJson: String? = null,
            resultJson: String? = null,
            errorCode: String? = ERROR_EXECUTOR_UNAVAILABLE,
        ): McpToolResult = McpToolResult(
            status = McpToolStatus.Blocked.storageValue,
            message = message,
            resultJson = resultJson ?: JSONObject()
                .put("blocked", true)
                .put("phase", "phase4_boundary")
                .toString(),
            risk = McpRiskLevel.High,
            toolName = toolName,
            argumentsJson = argumentsJson,
            errorCode = errorCode,
        )

        fun notImplemented(
            toolName: String,
            message: String = "该 MCP 工具尚未实现",
            argumentsJson: String? = null,
        ): McpToolResult = McpToolResult(
            status = McpToolStatus.NotImplemented.storageValue,
            message = message,
            resultJson = JSONObject()
                .put("not_implemented", true)
                .put("tool_name", toolName)
                .toString(),
            risk = McpRiskLevel.Low,
            toolName = toolName,
            argumentsJson = argumentsJson,
            errorCode = ERROR_UNSUPPORTED_TOOL,
        )

        fun requiresConfirmation(
            message: String,
            confirmationId: String,
            resultJson: String? = null,
            toolName: String? = null,
            commandLogId: Long? = null,
            affectedNoteIds: List<Long> = emptyList(),
            affectedTagIds: List<Long> = emptyList(),
            summary: String? = null,
            previewJson: String? = null,
            expiresAt: Long? = null,
        ): McpToolResult = McpToolResult(
            status = McpToolStatus.RequiresConfirmation.storageValue,
            message = message,
            resultJson = resultJson,
            risk = McpRiskLevel.High,
            requiresConfirmation = true,
            confirmationId = confirmationId,
            confirmationSummary = summary,
            confirmationPreviewJson = previewJson,
            expiresAt = expiresAt,
            commandLogId = commandLogId,
            affectedNoteIds = affectedNoteIds,
            affectedTagIds = affectedTagIds,
            toolName = toolName,
            errorCode = ERROR_REQUIRES_CONFIRMATION,
        )

        const val ERROR_INVALID_JSON = "invalid_json"
        const val ERROR_VALIDATION = "validation_error"
        const val ERROR_EXECUTOR_UNAVAILABLE = "executor_unavailable"
        const val ERROR_UNSUPPORTED_TOOL = "unsupported_tool"
        const val ERROR_REQUIRES_CONFIRMATION = "requires_confirmation"
    }
}

internal fun String.toJsonObjectOrArrayOrString(): Any {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> trimmed
        }
    }.getOrDefault(trimmed)
}
