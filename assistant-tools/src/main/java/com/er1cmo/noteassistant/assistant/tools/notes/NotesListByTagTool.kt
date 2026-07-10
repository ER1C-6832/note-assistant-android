package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class NotesListByTagTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "notes.list_by_tag"
    override val description: String =
        "只读地按标签列出便签，并同步显示标签过滤列表。如果同一句话要求归档/取消归档，不要先调用本工具；直接调用 notes.archive 并传 tag_name。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "tag_id": { "type": "integer" },
                "tag_name": { "type": "string" },
                "tag": { "type": "string" },
                "limit": { "type": "integer" },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "列出客户标签下的便签",
            "列出 tag_id=3 的便签",
            "若用户说‘把客户标签下那一条归档’，应改用 notes.archive {\"tag_name\":\"客户\",\"archived\":true}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "notes.list_by_tag 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val tagId = parser.optionalLong("tag_id")?.takeIf { it > 0L }
        val tagName = parser.optionalString("tag_name", "").ifBlank { parser.optionalString("tag", "") }.trimStart('#')
        if (tagId == null && tagName.isBlank()) {
            return McpToolResult.failed(
                message = "缺少 tag_id 或 tag_name",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = McpToolResult.ERROR_VALIDATION,
                risk = McpRiskLevel.Low,
            )
        }

        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val includeArchived = parser.boolean("include_archived", false)
        val includeDeleted = parser.boolean("include_deleted", false)
        val notes = buildList {
            addAll(noteUseCases.listNotes().first())
            if (includeArchived) addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted) addAll(noteUseCases.listDeletedNotes().first())
        }
            .distinctBy { it.id }
            .filter { note ->
                note.tags.any { tag ->
                    (tagId != null && tag.id == tagId) ||
                        (tagName.isNotBlank() && (tag.name.equals(tagName, ignoreCase = true) || tag.normalizedName == tagName.lowercase()))
                }
            }
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            .take(limit)

        uiCommandBus.emit(UiCommand.ShowTag(tagId = tagId, tagName = tagName))
        val tagLabel = tagName.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "ID=$tagId"
        val nextStepHint = when (notes.size) {
            0 -> "No note has this tag in the requested scope. Stop; do not reinterpret the tag name as a note title."
            1 -> "Exactly one tagged note was returned. If the user also wants to archive it, call notes.archive with tag_name=$tagName and archived=true. Do not replace the tag condition with a title keyword or an older note_id."
            else -> "Multiple tagged notes were returned. For archive, call notes.archive with tag_name=$tagName only when the user explicitly says all/所有 and set allow_multiple=true; otherwise ask for a title."
        }
        val archiveHandoff = JSONObject()
            .put("tool", "notes.archive")
            .put("tag_name", tagName.ifBlank { JSONObject.NULL })
            .put("rule", "Tag identity remains authoritative. Do not convert tag_name into a title query.")
            .put("single_match_arguments", JSONObject().put("tag_name", tagName).put("archived", true))
            .put("all_matches_arguments", JSONObject().put("tag_name", tagName).put("allow_multiple", true).put("archived", true))
        val resultJson = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("kind", "by_tag")
            .put("tag_id", tagId ?: JSONObject.NULL)
            .put("tag_name", tagName.ifBlank { JSONObject.NULL })
            .put("count", notes.size)
            .put("results", notes.toAssistantNoteResultsJsonArray())
            .put("ui_effect", "show_tag")
            .put("archive_handoff", archiveHandoff)
            .put("assistant_next_step_hint", nextStepHint)
            .toString()
        return McpToolResult.success(
            message = notes.toAssistantReadableListMessage("标签 $tagLabel 下的便签"),
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
            affectedTagIds = tagId?.let { listOf(it) } ?: emptyList(),
        )
    }
}
