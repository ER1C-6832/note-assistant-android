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

class NotesConvertTypeTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.convert_type"
    override val description: String =
        "在普通便签和待办便签之间转换。优先用 note_ref/title/query 定位用户看得见的便签，不要复用旧 note_id。转换为待办时可同时设置 done。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer", "description": "内部 ID。仅当来自当前工具结果时使用。" },
                "note_ref": { "type": "string", "description": "用户可见标题或关键词，例如 手柄。优先使用。" },
                "note_title": { "type": "string" },
                "title": { "type": "string" },
                "query": { "type": "string" },
                "exact_title": { "type": "string" },
                "scope": { "type": "string", "enum": ["active", "archived", "active_archived", "all"] },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "target_type": { "type": "string", "enum": ["normal", "todo"] },
                "done": { "type": "boolean", "description": "仅转换为 todo 时有效，可同时设置完成状态。" }
              },
              "additionalProperties": true
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "把手柄便签转成待办：{\"note_ref\":\"手柄\",\"target_type\":\"todo\"}",
            "把手柄便签转成待办并标记完成：{\"note_ref\":\"手柄\",\"target_type\":\"todo\",\"done\":true}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.convert_type 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val targetType = raw.optString("target_type", raw.optString("type", "")).trim()
        if (targetType.isBlank()) {
            return McpToolResult.failed("缺少 target_type 或 type", name, argumentsJson, McpToolResult.ERROR_VALIDATION, McpRiskLevel.Medium)
        }
        val target = resolveTarget(raw, argumentsJson) ?: return McpToolResult.failed(
            message = "缺少 note_id、note_ref、note_title、title、query 或 exact_title。已停止执行。",
            toolName = name,
            argumentsJson = argumentsJson,
            errorCode = McpToolResult.ERROR_VALIDATION,
            risk = McpRiskLevel.Medium,
        )
        if (target.error != null) return target.error
        val nextArguments = JSONObject(raw.toString())
            .put("note_id", target.noteId)
            .put("target_type", targetType)
        nextArguments.remove("note_ref")
        nextArguments.remove("note_title")
        nextArguments.remove("title")
        nextArguments.remove("query")
        nextArguments.remove("exact_title")
        nextArguments.remove("scope")

        val commandResult = commandService.execute(name, nextArguments.toString(), context.toCommandSource())
        return commandResult.toMcpToolResult(toolName = name, argumentsJson = nextArguments.toString())
    }

    private suspend fun resolveTarget(raw: JSONObject, argumentsJson: String): ConvertTarget? {
        val visibleQuery = raw.visibleReference()
        val noteId = raw.optLong("note_id", 0L).takeIf { it > 0L }
        if (visibleQuery.isBlank()) return noteId?.let { ConvertTarget(noteId = it) }
        val result = resolver.resolve(
            NoteResolveRequest(
                query = visibleQuery,
                exactTitle = raw.exactTitleReference(),
                scope = raw.optString("scope", "active_archived").toNoteResolveScope(defaultScope = NoteResolveScope.ActiveAndArchived),
                limit = 3,
            ),
        )
        if (result.totalMatches != 1 || result.matches.size != 1) {
            return ConvertTarget(
                noteId = 0L,
                error = McpToolResult.failed(
                    message = if (result.totalMatches == 0) "没有找到要转换的便签：$visibleQuery。已停止执行。" else "找到 ${result.totalMatches} 条可能的便签，请说出更具体标题后再转换。已停止执行。",
                    toolName = name,
                    argumentsJson = argumentsJson,
                    errorCode = McpToolResult.ERROR_VALIDATION,
                    risk = McpRiskLevel.Medium,
                ),
            )
        }
        val resolved = result.matches.first()
        if (noteId != null && noteId != resolved.id) {
            return ConvertTarget(
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
        return ConvertTarget(resolved.id)
    }
}

private data class ConvertTarget(
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
