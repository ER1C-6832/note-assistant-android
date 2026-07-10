package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.notes.NoteReferenceResolver
import com.er1cmo.noteassistant.assistant.tools.notes.NoteResolveScope
import com.er1cmo.noteassistant.assistant.tools.notes.PreparedNoteArguments
import com.er1cmo.noteassistant.assistant.tools.notes.prepareResolvedNoteArguments
import javax.inject.Inject
import org.json.JSONObject

class UiOpenNoteTool @Inject constructor(
    private val resolver: NoteReferenceResolver,
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.open_note"
    override val description: String =
        "打开唯一目标便签详情页。语音入口优先传 note_ref/title；不要复用旧 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见标题或唯一关键词" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string" },
                "title": { "type": "string", "description": "目标标题兼容字段" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "id_is_internal": { "type": "boolean" },
                "include_deleted": { "type": "boolean" },
                "scope": { "type": "string", "enum": ["active", "archived", "deleted", "all"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "打开验收客户回访：{\"note_ref\":\"验收客户回访\"}",
            "打开当前结果 ID：{\"note_id\":12,\"id_is_internal\":true}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val raw = runCatching { JSONObject(argumentsJson.trim().ifBlank { "{}" }) }.getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "ui.open_note 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val scope = when (raw.optString("scope", "").lowercase()) {
            "active" -> NoteResolveScope.Active
            "archived" -> NoteResolveScope.Archived
            "deleted" -> NoteResolveScope.Deleted
            "all" -> NoteResolveScope.All
            else -> if (raw.optBoolean("include_deleted", false)) NoteResolveScope.All else NoteResolveScope.ActiveAndArchived
        }
        return when (
            val prepared = prepareResolvedNoteArguments(
                toolName = name,
                argumentsJson = argumentsJson,
                context = context,
                risk = riskLevel,
                resolver = resolver,
                scope = scope,
                supportsMultiple = false,
                referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query", "title"),
            )
        ) {
            is PreparedNoteArguments.Failed -> prepared.result
            is PreparedNoteArguments.Ready -> {
                val note = prepared.notes.single()
                if (note.deleted && !raw.optBoolean("include_deleted", false) && scope != NoteResolveScope.Deleted && scope != NoteResolveScope.All) {
                    return McpToolResult.failed(
                        message = "便签《${note.title.ifBlank { "未命名便签" }}》在最近删除中，未打开。",
                        toolName = name,
                        argumentsJson = argumentsJson,
                        errorCode = "already_deleted",
                        risk = McpRiskLevel.Low,
                    )
                }
                uiCommandBus.emit(UiCommand.OpenNote(noteId = note.id))
                McpToolResult.success(
                    message = "已打开便签：《${note.title.ifBlank { "未命名便签" }}》",
                    resultJson = JSONObject()
                        .put("opened", true)
                        .put("note_id", note.id)
                        .put("note_ref", note.title.ifBlank { "未命名便签" })
                        .put("title", note.title)
                        .toString(),
                    toolName = name,
                    risk = McpRiskLevel.Low,
                    affectedNoteIds = listOf(note.id),
                )
            }
        }
    }
}
