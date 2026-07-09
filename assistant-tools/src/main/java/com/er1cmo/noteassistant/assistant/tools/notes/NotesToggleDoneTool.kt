package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject
import org.json.JSONObject

class NotesToggleDoneTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.toggle_done"
    override val description: String =
        "把便签标记完成或取消完成。优先使用 note_ref/title/query 定位用户看得见的便签；普通便签 done=true 时会先转换为待办再标记完成。不要复用旧 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer", "description": "内部 ID。仅当来自当前 resolve/search/get 结果时使用。" },
                "note_ref": { "type": "string", "description": "用户可见标题或关键词，例如 手柄。优先使用。" },
                "note_title": { "type": "string", "description": "用户可见标题。" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段。" },
                "query": { "type": "string", "description": "用户话术或关键词，例如 把手柄便签标记完成。" },
                "exact_title": { "type": "string", "description": "精确标题。" },
                "scope": { "type": "string", "enum": ["active", "archived", "active_archived", "all"] },
                "done": { "type": "boolean", "description": "true 表示标记完成；false 表示取消完成。缺省时切换当前完成状态。" },
                "auto_convert_to_todo": { "type": "boolean", "description": "普通便签被标记完成时是否自动转为待办；默认 true。" }
              },
              "additionalProperties": true
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "把手柄便签标记完成：{\"note_ref\":\"手柄\",\"done\":true}",
            "把刚才那条标记完成：{\"note_id\":123,\"done\":true}",
            "普通便签标记完成时会先转为待办：{\"note_ref\":\"手柄\",\"done\":true,\"auto_convert_to_todo\":true}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.toggle_done 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val target = resolveTarget(raw, argumentsJson) ?: return noTarget(raw, argumentsJson)
        if (target.error != null) return target.error

        val nextArguments = JSONObject(raw.toString())
            .put("note_id", target.noteId)
            .put("auto_convert_to_todo", if (raw.has("auto_convert_to_todo")) raw.optBoolean("auto_convert_to_todo", true) else true)
        nextArguments.remove("note_ref")
        nextArguments.remove("note_title")
        nextArguments.remove("title")
        nextArguments.remove("query")
        nextArguments.remove("exact_title")
        nextArguments.remove("scope")

        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = nextArguments.toString(),
            source = context.toCommandSource(),
        )
        return commandResult.toMcpToolResult(toolName = name, argumentsJson = nextArguments.toString())
    }

    private suspend fun resolveTarget(raw: JSONObject, argumentsJson: String): ToggleTarget? {
        val visibleQuery = raw.visibleReference()
        val noteId = raw.optLong("note_id", 0L).takeIf { it > 0L }
        if (visibleQuery.isBlank()) return noteId?.let { ToggleTarget(noteId = it) }

        val exactTitle = raw.exactTitleReference()
        val result = resolver.resolve(
            NoteResolveRequest(
                query = visibleQuery,
                exactTitle = exactTitle,
                scope = raw.optString("scope", "active_archived").toNoteResolveScope(defaultScope = NoteResolveScope.ActiveAndArchived),
                limit = 3,
            ),
        )
        if (result.totalMatches == 0) {
            return ToggleTarget(
                noteId = 0L,
                error = McpToolResult.failed(
                    message = "没有找到要标记完成的便签：${visibleQuery}。已停止执行，不会改动其他便签。",
                    toolName = name,
                    argumentsJson = argumentsJson,
                    errorCode = McpToolResult.ERROR_VALIDATION,
                    risk = McpRiskLevel.Medium,
                ),
            )
        }
        if (result.totalMatches > 1 || result.matches.size != 1) {
            return ToggleTarget(
                noteId = 0L,
                error = McpToolResult.failed(
                    message = "找到 ${result.totalMatches} 条可能的便签，请说出更具体标题后再标记完成。已停止执行。",
                    toolName = name,
                    argumentsJson = argumentsJson,
                    errorCode = McpToolResult.ERROR_VALIDATION,
                    risk = McpRiskLevel.Medium,
                ),
            )
        }
        val resolved = result.matches.first()
        if (noteId != null && noteId != resolved.id) {
            return ToggleTarget(
                noteId = 0L,
                error = McpToolResult.failed(
                    message = "参数冲突：note_id=$noteId 但用户可见引用“$visibleQuery”匹配到 note_id=${resolved.id}《${resolved.title.ifBlank { "未命名便签" }}》。已停止执行，避免改错便签。",
                    toolName = name,
                    argumentsJson = argumentsJson,
                    errorCode = "note_reference_mismatch",
                    risk = McpRiskLevel.Medium,
                ),
            )
        }
        return ToggleTarget(noteId = resolved.id)
    }

    private fun noTarget(raw: JSONObject, argumentsJson: String): McpToolResult {
        val spoken = raw.optString("query", raw.optString("note_ref", ""))
        val hint = if (spoken.isBlank()) "缺少 note_id、note_ref、note_title、title、query 或 exact_title" else "没有找到目标便签：$spoken"
        return McpToolResult.failed(
            message = "$hint。已停止执行，不会改动其他便签。",
            toolName = name,
            argumentsJson = argumentsJson,
            errorCode = McpToolResult.ERROR_VALIDATION,
            risk = McpRiskLevel.Medium,
        )
    }
}

private data class ToggleTarget(
    val noteId: Long,
    val error: McpToolResult? = null,
)

private fun JSONObject.visibleReference(): String = listOf(
    optString("note_ref", ""),
    optString("note_title", ""),
    optString("title", ""),
    optString("exact_title", ""),
    optString("query", ""),
).firstOrNull { it.isNotBlank() }?.toAssistantReferenceText().orEmpty()

private fun JSONObject.exactTitleReference(): String = listOf(
    optString("exact_title", ""),
    optString("note_title", ""),
    optString("title", ""),
).firstOrNull { it.isNotBlank() }?.toAssistantReferenceText().orEmpty()
