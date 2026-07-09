package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.FailClosedMcpToolExecutor
import com.er1cmo.noteassistant.assistant.mcpbase.McpJsonRpcMapper
import com.er1cmo.noteassistant.assistant.mcpbase.McpResultMapper
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.mcpbase.putIdJson
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallEventStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

@Singleton
class McpProtocolClient @Inject constructor(
    private val executors: Set<@JvmSuppressWildcards McpToolExecutor>,
    private val toolCallEventStore: ToolCallEventStore,
) {
    constructor() : this(emptySet(), ToolCallEventStore())

    private val failClosedExecutor = FailClosedMcpToolExecutor()

    fun toolsList(): List<McpToolDescriptor> = activeExecutor().listDescriptors()

    fun handleJsonRpc(payloadJson: String): McpProtocolResponse {
        val request = McpJsonRpcMapper.parseRequest(payloadJson).getOrElse { error ->
            return McpProtocolResponse.error(
                requestIdJson = null,
                method = null,
                message = error.message ?: "Invalid MCP JSON-RPC payload",
                errorCode = McpToolResult.ERROR_INVALID_JSON,
                errorCodeNumber = McpResultMapper.ERROR_PARSE,
            )
        }

        return when (request.method) {
            "initialize" -> McpProtocolResponse.success(
                requestIdJson = request.requestIdJson,
                method = request.method,
                message = "Phase4 MCP initialize acknowledged; tools are delegated through injected executor.",
                responseJson = buildInitializeResponseJson(request.requestIdJson),
            )
            "notifications/initialized" -> McpProtocolResponse.success(
                requestIdJson = request.requestIdJson,
                method = request.method,
                message = "Phase4 MCP initialized notification acknowledged.",
                responseJson = buildInitializedNotificationAckJson(request.requestIdJson),
            )
            "tools/list" -> McpProtocolResponse.success(
                requestIdJson = request.requestIdJson,
                method = request.method,
                message = "MCP tools/list returned injected executor descriptors.",
                responseJson = McpResultMapper.toolsListResponse(
                    requestIdJson = request.requestIdJson,
                    descriptors = toolsList(),
                ),
            )
            "tools/call" -> handleToolsCall(request.payload, request.requestIdJson, request.method)
            else -> McpProtocolResponse.error(
                requestIdJson = request.requestIdJson,
                method = request.method,
                message = "Unsupported MCP method: ${request.method}",
                errorCode = McpToolResult.ERROR_UNSUPPORTED_TOOL,
                errorCodeNumber = McpResultMapper.ERROR_METHOD_NOT_FOUND,
            )
        }
    }

    fun handleToolCall(toolName: String, argumentsJson: String): McpToolResult = runBlocking {
        toolCallEventStore.markRunning(toolName, argumentsJson)
        val result = activeExecutor().execute(
            name = toolName,
            argumentsJson = argumentsJson,
            context = McpToolContext(),
        ).withToolNameIfMissing(toolName)
        toolCallEventStore.markCompleted(result)
        result
    }

    private fun handleToolsCall(
        payload: JSONObject,
        requestIdJson: String?,
        method: String,
    ): McpProtocolResponse {
        val call = McpJsonRpcMapper.parseToolCall(payload).getOrElse { error ->
            val result = McpToolResult.failed(
                message = error.message ?: "tools/call 参数无效",
                errorCode = McpToolResult.ERROR_VALIDATION,
            )
            toolCallEventStore.markFailed(
                toolName = null,
                message = result.message,
                errorCode = result.errorCode,
            )
            return McpProtocolResponse(
                requestIdJson = requestIdJson,
                method = method,
                toolName = null,
                status = result.statusEnum,
                blocked = true,
                message = result.message,
                responseJson = McpResultMapper.toolsCallResponse(requestIdJson, result),
            )
        }

        toolCallEventStore.markRunning(call.toolName, call.argumentsJson)
        val result = runBlocking {
            activeExecutor().execute(
                name = call.toolName,
                argumentsJson = call.argumentsJson,
                context = McpToolContext(requestIdJson = call.requestIdJson),
            ).withToolNameIfMissing(call.toolName)
        }
        toolCallEventStore.markCompleted(result)
        return McpProtocolResponse(
            requestIdJson = call.requestIdJson,
            method = method,
            toolName = result.toolName,
            status = result.statusEnum,
            blocked = result.statusEnum != McpToolStatus.Success,
            message = result.message,
            responseJson = McpResultMapper.toolsCallResponse(call.requestIdJson, result),
        )
    }

    private fun activeExecutor(): McpToolExecutor {
        if (executors.isEmpty()) return failClosedExecutor
        if (executors.size == 1) return executors.first()
        return CompositeMcpToolExecutor(executors.toList())
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
                            .put("name", "note-assistant-android-phase4")
                            .put("version", "0.1.0-phase4"),
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
}

private fun McpToolResult.withToolNameIfMissing(toolName: String): McpToolResult {
    return if (this.toolName.isNullOrBlank()) copy(toolName = toolName) else this
}

private class CompositeMcpToolExecutor(
    private val delegates: List<McpToolExecutor>,
) : McpToolExecutor {
    override fun listDescriptors(): List<McpToolDescriptor> = delegates
        .flatMap { it.listDescriptors() }
        .distinctBy { it.name }
        .sortedBy { it.name }

    override fun findTool(name: String): McpTool? = delegates
        .asSequence()
        .mapNotNull { it.findTool(name) }
        .firstOrNull()

    override suspend fun execute(
        name: String,
        argumentsJson: String,
        context: McpToolContext,
    ): McpToolResult {
        val executor = delegates.firstOrNull { it.findTool(name) != null }
            ?: return McpToolResult.notImplemented(
                toolName = name,
                message = "暂不支持该工具：$name",
                argumentsJson = argumentsJson,
            )
        return executor.execute(name, argumentsJson, context)
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
            errorCode: String = McpToolResult.ERROR_UNSUPPORTED_TOOL,
            errorCodeNumber: Int = McpResultMapper.ERROR_METHOD_NOT_FOUND,
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
                code = errorCodeNumber,
                data = JSONObject().put("error_code", errorCode),
            ),
        )
    }
}
