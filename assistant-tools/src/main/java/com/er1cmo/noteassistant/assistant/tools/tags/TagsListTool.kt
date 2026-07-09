package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class TagsListTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "tags.list"
    override val description: String = "列出所有标签，可带关联便签数量。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "include_counts": { "type": "boolean" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("列出所有标签"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "tags.list 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val includeCounts = parser.boolean("include_counts", true)
        val tags = noteUseCases.listTags().first()
        val notes = if (includeCounts) activeAndInactiveNotes() else emptyList()
        val result = JSONObject()
            .put("count", tags.size)
            .put("results", JSONArray().also { array ->
                tags.forEach { tag -> array.put(tag.toJsonObject(if (includeCounts) notes.countLinked(tag) else 0)) }
            })
            .toString()
        return McpToolResult.success("已列出 ${tags.size} 个标签", result, name, McpRiskLevel.Low, affectedTagIds = tags.map { it.id })
    }

    private suspend fun activeAndInactiveNotes(): List<Note> = buildList {
        addAll(noteUseCases.listNotes().first())
        addAll(noteUseCases.listArchivedNotes().first())
        addAll(noteUseCases.listDeletedNotes().first())
    }.distinctBy { it.id }

    private fun List<Note>.countLinked(tag: Tag): Int = count { note -> note.tags.any { it.id == tag.id || it.normalizedName == tag.normalizedName } }

    private fun Tag.toJsonObject(linkedNoteCount: Int): JSONObject = JSONObject()
        .put("tag_id", id)
        .put("name", name)
        .put("color", color ?: JSONObject.NULL)
        .put("linked_note_count", linkedNoteCount)
}
