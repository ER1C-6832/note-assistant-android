package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.McpResultMapper
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.mcpbase.requestIdJson
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class McpProtocolClient @Inject constructor() {
    fun toolsList(): List<McpToolDescriptor> = listOf(
        McpToolDescriptor(
            name = "phase3.echo",
            description = "Phase3 fake runtime echo tool. It does not read or mutate notes.",
            inputSchemaJson = "{\"type\":\"object\",\"additionalProperties\":true}",
            riskLevel = McpRiskLevel.Low,
            mutates = false,
        ),
        McpToolDescriptor(
            name = "phase3.status",
            description = "Returns assistant runtime status metadata only.",
            inputSchemaJson = "{\"type\":\"object\",\"additionalProperties\":true}",
            riskLevel = McpRiskLevel.Low,
            mutates = false,
        ),
    )

    fun handleJsonRpc(payloadJson: String): McpProtocolResponse {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
            ?: return McpProtocolResponse.error(
                requestIdJson = null,
                method = null,
                message = "Invalid MCP JSON-RPC payload",
            )
        val requestIdJson = payload.requestIdJson()
        val method = payload.optString("method").trim()
        return when (method) {
            "initialize" -> McpProtocolResponse.success(
                requestIdJson = requestIdJson,
                method = method,
                message = "Phase3 MCP initialize acknowledged; note mutation tools remain blocked.",
                responseJson = buildInitializeResponseJson(requestIdJson),
            )
            "notifications/initialized" -> McpProtocolResponse.success(
                requestIdJson = requestIdJson,
                method = method,
                message = "Phase3 MCP initialized notification acknowledged.",
                responseJson = buildInitializedNotificationAckJson(requestIdJson),
            )
            "tools/list" -> McpProtocolResponse.success(
                requestIdJson = requestIdJson,
                method = method,
                message = "Phase3 tools/list returned safe runtime-only tools.",
                responseJson = McpResultMapper.toolsListResponse(requestIdJson, toolsList()),
            )
            "tools/call" -> handleToolsCall(payload = payload, requestIdJson = requestIdJson, method = method)
            else -> McpProtocolResponse.error(
                requestIdJson = requestIdJson,
                method = method.ifBlank { null },
                message = "Phase3 unsupported MCP method: ${method.ifBlank { "<missing>" }}",
            )
        }
    }

    fun handleToolCall(toolName: String, argumentsJson: String): McpToolResult {
        val normalized = toolName.trim().lowercase()
        return when {
            isNoteMutationTool(normalized) -> McpToolResult.blocked(
                toolName = toolName,
                message = "Phase3 已收到工具调用，但便签工具执行被阻断；真实执行从 Phase4 开始并必须走 NoteCommandService。",
                argumentsJson = argumentsJson,
                resultJson = JSONObject()
                    .put("blocked", true)
                    .put("phase", "phase3")
                    .put("note_mutation_enabled", false)
                    .toString(),
            )
            normalized == "phase3.echo" -> McpToolResult.success(
                toolName = toolName,
                message = "Phase3 echo ok",
                resultJson = JSONObject()
                    .put("echo", argumentsJson.toJsonObjectOrString())
                    .toString(),
            )
            normalized == "phase3.status" -> McpToolResult.success(
                toolName = toolName,
                message = "Phase3 runtime status tool is available",
                resultJson = JSONObject()
                    .put("phase3", true)
                    .put("note_mutation_enabled", false)
                    .put("boundary", "mcp_protocol_block")
                    .toString(),
            )
            else -> McpToolResult.notImplemented(
                toolName = toolName,
                message = "Phase3 暂不支持该工具：$toolName",
                argumentsJson = argumentsJson,
            )
        }
    }

    private fun handleToolsCall(
        payload: JSONObject,
        requestIdJson: String?,
        method: String,
    ): McpProtocolResponse {
        val params = payload.optJSONObject("params") ?: JSONObject()
        val toolName = params.optString("name").ifBlank {
            params.optString("tool").ifBlank { params.optString("tool_name") }
        }
        val argumentsAny = params.opt("arguments") ?: params.opt("args") ?: JSONObject()
        val argumentsJson = when (argumentsAny) {
            is JSONObject -> argumentsAny.toString()
            is JSONArray -> argumentsAny.toString()
            is String -> argumentsAny.ifBlank { "{}" }
            else -> JSONObject().put("value", argumentsAny.toString()).toString()
        }
        if (toolName.isBlank()) {
            return McpProtocolResponse.error(
                requestIdJson = requestIdJson,
                method = method,
                message = "tools/call 缺少 name/tool_name",
            )
        }
        val result = handleToolCall(toolName = toolName, argumentsJson = argumentsJson)
        return McpProtocolResponse(
            requestIdJson = requestIdJson,
            method = method,
            toolName = result.toolName,
            status = result.statusEnum,
            blocked = result.statusEnum != McpToolStatus.Success,
            message = result.message,
            responseJson = McpResultMapper.toolsCallResponse(requestIdJson, result),
        )
    }

    private fun buildInitializeResponseJson(requestIdJson: String?): String {
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put(
                "result",
                JSONObject()
                    .put("protocolVersion", "2024-11-05")
                    .put(
                        "capabilities",
                        JSONObject()
                            .put("tools", JSONObject().put("listChanged", false)),
                    )
                    .put(
                        "serverInfo",
                        JSONObject()
                            .put("name", "note-assistant-android-phase3")
                            .put("version", "0.1.0-phase3"),
                    ),
            )
            .toString()
    }

    private fun buildInitializedNotificationAckJson(requestIdJson: String?): String {
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put("result", JSONObject().put("acknowledged", true))
            .toString()
    }

    private fun isNoteMutationTool(normalizedToolName: String): Boolean {
        if (normalizedToolName.startsWith("notes.")) return true
        if (normalizedToolName.startsWith("tags.")) return true
        return false
    }
}

data class McpProtocolResponse(
    val requestIdJson: String?,
    val method: String?,
    val toolName: String?,
    val status: McpToolStatus,
    val blocked: Boolean,
    val message: String,
    val responseJson: String,
) {
    companion object {
        fun success(
            requestIdJson: String?,
            method: String,
            message: String,
            responseJson: String,
        ): McpProtocolResponse = McpProtocolResponse(
            requestIdJson = requestIdJson,
            method = method,
            toolName = null,
            status = McpToolStatus.Success,
            blocked = false,
            message = message,
            responseJson = responseJson,
        )

        fun error(
            requestIdJson: String?,
            method: String?,
            message: String,
        ): McpProtocolResponse = McpProtocolResponse(
            requestIdJson = requestIdJson,
            method = method,
            toolName = null,
            status = McpToolStatus.NotImplemented,
            blocked = true,
            message = message,
            responseJson = McpResultMapper.errorResponse(
                requestIdJson = requestIdJson,
                message = message,
                data = JSONObject().put("phase", "phase3"),
            ),
        )
    }
}

private fun JSONObject.putIdJson(idJson: String?): JSONObject {
    if (idJson == null) {
        put("id", JSONObject.NULL)
        return this
    }
    val id = runCatching { JSONObject("{\"id\":$idJson}").opt("id") }.getOrNull()
    put("id", id ?: JSONObject.NULL)
    return this
}

private fun String.toJsonObjectOrString(): Any {
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
