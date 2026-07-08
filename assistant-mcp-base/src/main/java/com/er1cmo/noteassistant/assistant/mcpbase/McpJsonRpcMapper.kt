package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONArray
import org.json.JSONObject

data class McpJsonRpcRequest(
    val requestIdJson: String?,
    val method: String,
    val payload: JSONObject,
)

data class McpToolCallRequest(
    val requestIdJson: String?,
    val toolName: String,
    val argumentsJson: String,
)

object McpJsonRpcMapper {
    fun parseRequest(payloadJson: String): Result<McpJsonRpcRequest> = runCatching {
        val payload = JSONObject(payloadJson)
        val method = payload.optString("method").trim()
        require(method.isNotBlank()) { "MCP JSON-RPC method 缺失" }
        McpJsonRpcRequest(
            requestIdJson = payload.requestIdJson(),
            method = method,
            payload = payload,
        )
    }

    fun parseToolCall(payload: JSONObject): Result<McpToolCallRequest> = runCatching {
        val params = payload.optJSONObject("params") ?: JSONObject()
        val toolName = params.optString("name").ifBlank {
            params.optString("tool").ifBlank { params.optString("tool_name") }
        }.trim()
        require(toolName.isNotBlank()) { "tools/call 缺少 name/tool_name" }
        McpToolCallRequest(
            requestIdJson = payload.requestIdJson(),
            toolName = toolName,
            argumentsJson = params.extractArgumentsJson(),
        )
    }

    suspend fun handleJsonRpc(
        payloadJson: String,
        executor: McpToolExecutor,
        context: McpToolContext = McpToolContext(),
    ): String {
        val request = parseRequest(payloadJson).getOrElse { error ->
            return McpResultMapper.errorResponse(
                requestIdJson = null,
                message = error.message ?: "Invalid MCP JSON-RPC payload",
                code = McpResultMapper.ERROR_PARSE,
                data = JSONObject().put("error_code", McpToolResult.ERROR_INVALID_JSON),
            )
        }
        return when (request.method) {
            "tools/list" -> McpResultMapper.toolsListResponse(
                requestIdJson = request.requestIdJson,
                descriptors = executor.listDescriptors(),
            )
            "tools/call" -> handleToolsCall(request.payload, executor, context)
            else -> McpResultMapper.errorResponse(
                requestIdJson = request.requestIdJson,
                message = "Unsupported MCP method: ${request.method}",
                code = McpResultMapper.ERROR_METHOD_NOT_FOUND,
                data = JSONObject().put("error_code", McpToolResult.ERROR_UNSUPPORTED_TOOL),
            )
        }
    }

    suspend fun handleToolsCall(
        payload: JSONObject,
        executor: McpToolExecutor,
        context: McpToolContext = McpToolContext(),
    ): String {
        val call = parseToolCall(payload).getOrElse { error ->
            return McpResultMapper.toolsCallResponse(
                requestIdJson = payload.requestIdJson(),
                result = McpToolResult.failed(
                    message = error.message ?: "tools/call 参数无效",
                    errorCode = McpToolResult.ERROR_VALIDATION,
                ),
            )
        }
        val result = executor.execute(
            name = call.toolName,
            argumentsJson = call.argumentsJson,
            context = context.copy(requestIdJson = call.requestIdJson),
        )
        return McpResultMapper.toolsCallResponse(call.requestIdJson, result)
    }

    private fun JSONObject.extractArgumentsJson(): String {
        val argumentsAny = opt("arguments") ?: opt("args") ?: JSONObject()
        return when (argumentsAny) {
            is JSONObject -> argumentsAny.toString()
            is JSONArray -> argumentsAny.toString()
            is String -> argumentsAny.ifBlank { "{}" }
            else -> JSONObject().put("value", argumentsAny.toString()).toString()
        }
    }
}
