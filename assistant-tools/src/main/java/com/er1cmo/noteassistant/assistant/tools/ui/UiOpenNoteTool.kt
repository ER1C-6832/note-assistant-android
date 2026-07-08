package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import org.json.JSONObject

class UiOpenNoteTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.open_note"
    override val description: String = "在当前 App 进程内打开指定 note_id 的便签详情页。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "include_deleted": { "type": "boolean" }
              },
              "required": ["note_id"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("打开刚才创建的便签", "打开 note_id=12"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "ui.open_note 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val noteId = runCatching { parser.requireLong("note_id") }.getOrElse { error ->
            return McpToolResult.failed(
                message = error.message ?: "缺少 note_id",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "validation_error",
                risk = McpRiskLevel.Low,
            )
        }
        val includeDeleted = parser.boolean("include_deleted", false)
        val note = noteUseCases.getNote(noteId)
            ?: return McpToolResult.failed(
                message = "没有找到便签：$noteId",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "not_found",
                risk = McpRiskLevel.Low,
            )
        if (note.deleted && !includeDeleted) {
            return McpToolResult.failed(
                message = "便签已在最近删除中，未打开。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "already_deleted",
                risk = McpRiskLevel.Low,
            )
        }
        uiCommandBus.emit(UiCommand.OpenNote(noteId = note.id))
        return McpToolResult.success(
            message = "已打开便签：${note.title.ifBlank { "未命名便签" }}",
            resultJson = JSONObject()
                .put("opened", true)
                .put("note_id", note.id)
                .put("title", note.title)
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = listOf(note.id),
        )
    }
}
