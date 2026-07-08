package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONArray
import org.json.JSONObject

object McpResultMapper {
    fun toolsListResponse(requestIdJson: String?, descriptors: List<McpToolDescriptor>): String {
        val tools = JSONArray()
        descriptors.forEach { tools.put(it.toJsonObject()) }
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put("result", JSONObject().put("tools", tools))
            .toString()
    }

    fun toolsCallResponse(requestIdJson: String?, result: McpToolResult): String {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "text")
                .put("text", result.message),
        )
        val envelope = result.toEnvelopeJsonObject()
        return JSONObject()
            .put("jsonrpc", "2.0")
            .putIdJson(requestIdJson)
            .put(
                "result",
                JSONObject()
                    .put("content", content)
                    .put("isError", result.statusEnum.isJsonRpcError)
                    .put("structuredContent", envelope),
            )
            .toString()
    }

    fun errorResponse(
        requestIdJson: String?,
        message: String,
        code: Int = ERROR_METHOD_NOT_FOUND,
        data: JSONObject = JSONObject(),
    ): String = JSONObject()
        .put("jsonrpc", "2.0")
        .putIdJson(requestIdJson)
        .put(
            "error",
            JSONObject()
                .put("code", code)
                .put("message", message)
                .put("data", data),
        )
        .toString()

    const val ERROR_PARSE = -32700
    const val ERROR_INVALID_REQUEST = -32600
    const val ERROR_METHOD_NOT_FOUND = -32601
    const val ERROR_INVALID_PARAMS = -32602
}

fun JSONObject.requestIdJson(): String? {
    if (!has("id") || isNull("id")) return null
    val id = opt("id") ?: return null
    return when (id) {
        is Number -> id.toString()
        is Boolean -> id.toString()
        else -> JSONObject.quote(id.toString())
    }
}

fun JSONObject.putIdJson(idJson: String?): JSONObject {
    if (idJson == null) {
        put("id", JSONObject.NULL)
        return this
    }
    val id = runCatching { JSONObject("{\"id\":$idJson}").opt("id") }.getOrNull()
    put("id", id ?: JSONObject.NULL)
    return this
}
