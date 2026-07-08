package com.er1cmo.noteassistant.assistant.runtime.mcp

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
            readOnly = true,
        ),
        McpToolDescriptor(
            name = "phase3.status",
            description = "Returns assistant runtime status metadata only.",
            readOnly = true,
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
            "tools/list" -> McpProtocolResponse.success(
                requestIdJson = requestIdJson,
                method = method,
                message = "Phase3 tools/list returned safe runtime-only tools.",
                responseJson = buildToolsListResponseJson(requestIdJson),
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
            status = result.status,
            blocked = result.status != McpToolStatus.Success,
            message = result.message,
            responseJson = result.toJsonRpcResponseJson(requestIdJson),
        )
    }

    private fun buildToolsListResponseJson(requestIdJson: String?): String {
        val tools = JSONArray()
        toolsList().forEach { descriptor ->
            tools.put(
                JSONObject()
                    .put("name", descriptor.name)
                    .put("description", descriptor.description)
                    .put(
                        "inputSchema",
                        JSONObject()
                            .put("type", "object")
                            .put("additionalProperties", true),
                    )
                    .put("readOnly", descriptor.readOnly),
            )
        }
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put("result", JSONObject().put("tools", tools))
            .toString()
    }

    private fun isNoteMutationTool(normalizedToolName: String): Boolean {
        if (normalizedToolName.startsWith("notes.")) return true
        if (normalizedToolName.startsWith("tags.")) return true
        return false
    }
}

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val readOnly: Boolean,
)

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
        ): McpProtocolResponse {
            val responseJson = JSONObject()
                .put("jsonrpc", "2.0")
                .putIdJson(requestIdJson)
                .put(
                    "error",
                    JSONObject()
                        .put("code", -32601)
                        .put("message", message)
                        .put("data", JSONObject().put("phase", "phase3")),
                )
                .toString()
            return McpProtocolResponse(
                requestIdJson = requestIdJson,
                method = method,
                toolName = null,
                status = McpToolStatus.NotImplemented,
                blocked = true,
                message = message,
                responseJson = responseJson,
            )
        }
    }
}

data class McpToolResult(
    val toolName: String,
    val status: McpToolStatus,
    val message: String,
    val resultJson: String? = null,
    val argumentsJson: String? = null,
) {
    fun toJsonRpcResponseJson(requestIdJson: String?): String {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "text")
                .put("text", message),
        )
        val structuredContent = JSONObject()
            .put("phase", "phase3")
            .put("toolName", toolName)
            .put("note_mutation_enabled", false)
            .put("blocked", status != McpToolStatus.Success)
            .put("status", status.storageValue)
        argumentsJson?.let { structuredContent.put("arguments", it.toJsonObjectOrString()) }
        resultJson?.let { structuredContent.put("result", it.toJsonObjectOrString()) }
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put(
                "result",
                JSONObject()
                    .put("content", content)
                    .put("isError", status != McpToolStatus.Success)
                    .put("structuredContent", structuredContent),
            )
            .toString()
    }

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
            resultJson = JSONObject()
                .put("blocked", true)
                .put("phase", "phase3")
                .put("note_mutation_enabled", false)
                .toString(),
        )

        fun notImplemented(toolName: String, message: String, argumentsJson: String): McpToolResult = McpToolResult(
            toolName = toolName,
            status = McpToolStatus.NotImplemented,
            message = message,
            argumentsJson = argumentsJson,
            resultJson = JSONObject()
                .put("not_implemented", true)
                .put("phase", "phase3")
                .put("note_mutation_enabled", false)
                .toString(),
        )
    }
}

enum class McpToolStatus(val storageValue: String) {
    Success("success"),
    Blocked("blocked"),
    NotImplemented("not_implemented"),
}

private fun JSONObject.requestIdJson(): String? {
    if (!has("id") || isNull("id")) return null
    val id = opt("id") ?: return null
    return when (id) {
        is Number -> id.toString()
        is Boolean -> id.toString()
        else -> JSONObject.quote(id.toString())
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
