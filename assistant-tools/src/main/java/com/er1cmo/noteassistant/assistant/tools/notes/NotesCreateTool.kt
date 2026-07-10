package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class NotesCreateTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.create"
    override val description: String =
        "仅在用户明确要求新建、创建或记录新内容时创建便签。归档/取消归档/恢复/置顶/删除不是创建；疑似归档同音词或已存在同名标题时必须先澄清。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "title": { "type": "string" },
                "content": { "type": "string" },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "tags": { "type": "array", "items": { "type": "string" } },
                "tag_text": { "type": "string" },
                "color": { "type": "string" },
                "pinned": { "type": "boolean" },
                "open_after_create": { "type": "boolean" },
                "allow_duplicate": { "type": "boolean", "description": "用户明确要求创建同名新便签时才设为 true" },
                "confirm_create": { "type": "boolean", "description": "标题确实以归档同音词开头且用户明确要求创建时才设为 true" }
              },
              "additionalProperties": true
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "新建一条便签，标题叫客户回访",
            "创建一个待办：周五寄出样品",
            "归档现有便签不得调用 notes.create",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val args = runCatching { JSONObject(argumentsJson.trim().ifBlank { "{}" }) }.getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.create 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val title = args.optString("title", "").trim()
        val content = args.optString("content", "").trim()
        val explicitlyConfirmedCreate = args.optBoolean("confirm_create", false)
        if (!explicitlyConfirmedCreate && listOf(title, content).any { it.startsWithPossibleArchiveAsrWord() }) {
            return McpToolResult.failed(
                message = "输入疑似把“归档”识别成了同音词，已停止创建，避免误生成便签。请确认是新建还是归档现有便签。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "possible_archive_asr_confusion",
                risk = McpRiskLevel.Medium,
            )
        }

        if (title.isNotBlank() && !args.optBoolean("allow_duplicate", false)) {
            val normalizedTitle = title.visibleTitleNormalize()
            val duplicate = buildList {
                addAll(noteUseCases.listNotes().first())
                addAll(noteUseCases.listArchivedNotes().first())
                addAll(noteUseCases.listDeletedNotes().first())
            }.distinctBy { it.id }.firstOrNull { it.title.visibleTitleNormalize() == normalizedTitle }
            if (duplicate != null) {
                return McpToolResult.failed(
                    message = "已存在同名便签《${duplicate.title.ifBlank { "未命名便签" }}》，已停止重复创建。若用户确实要新建同名便签，请明确说明并传 allow_duplicate=true。",
                    toolName = name,
                    argumentsJson = argumentsJson,
                    errorCode = "duplicate_title_requires_explicit_create",
                    risk = McpRiskLevel.Medium,
                ).copy(
                    resultJson = duplicate.toAssistantNoteResultJson()
                        .put("assistant_next_step_hint", "Do not create a duplicate by default. Use the requested mutation tool for the existing note, or ask the user to explicitly confirm a duplicate note.")
                        .toString(),
                    affectedNoteIds = listOf(duplicate.id),
                )
            }
        }

        args.remove("allow_duplicate")
        args.remove("confirm_create")
        val normalizedArguments = args.toString()
        return commandService.execute(
            toolName = name,
            argumentsJson = normalizedArguments,
            source = context.toCommandSource(),
        ).toMcpToolResult(
            toolName = name,
            argumentsJson = normalizedArguments,
        )
    }
}

private fun String.startsWithPossibleArchiveAsrWord(): Boolean {
    val normalized = trim().replace(Regex("^[，。,.!?！？:：;；\\s]+"), "")
    return listOf(
        "归档",
        "已归档",
        "轨档",
        "硅档",
        "硅党",
        "归堂",
        "归党",
        "归挡",
        "规档",
    ).any { normalized.startsWith(it) }
}
