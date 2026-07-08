package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

abstract class AbstractUiSurfaceTool(
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    abstract override val name: String
    abstract override val description: String
    abstract fun buildCommand(parser: ToolArgumentParser): UiCommand
    abstract fun resultPayload(parser: ToolArgumentParser): JSONObject

    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor
        get() = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = inputSchemaJson,
            riskLevel = McpRiskLevel.Low,
            mutates = false,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        )

    open val inputSchemaJson: String = "{\"type\":\"object\",\"additionalProperties\":true}"

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(toolName = name, argumentsJson = argumentsJson, message = "$name 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val command = buildCommand(parser)
        uiCommandBus.emit(command)
        return McpToolResult.success(
            message = "已切换界面：$name",
            resultJson = resultPayload(parser).put("ui_command", command.javaClass.simpleName).toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
        )
    }
}

class UiShowSearchTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_search"
    override val description: String = "打开便签列表并展示搜索语境。"
    override val inputSchemaJson: String = """
        {"type":"object","properties":{"query":{"type":"string"}},"additionalProperties":false}
    """.trimIndent()
    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowSearch(parser.optionalString("query"))
    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject().put("query", parser.optionalString("query"))
}

class UiShowNoteListTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_note_list"
    override val description: String = "打开普通便签列表。"
    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowNoteList
    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject().put("surface", "note_list")
}

class UiShowTagTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_tag"
    override val description: String = "打开标签语境下的便签列表。"
    override val inputSchemaJson: String = """
        {"type":"object","properties":{"tag_id":{"type":"integer"},"tag":{"type":"string"}},"additionalProperties":false}
    """.trimIndent()
    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowTag(tagId = parser.optionalLong("tag_id"), tagName = parser.optionalString("tag"))
    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject().put("tag_id", parser.optionalLong("tag_id") ?: JSONObject.NULL).put("tag", parser.optionalString("tag"))
}

class UiShowArchiveTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_archive"
    override val description: String = "打开归档便签列表。"
    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowArchive
    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject().put("surface", "archive")
}

class UiShowTrashTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_trash"
    override val description: String = "打开最近删除列表。"
    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowTrash
    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject().put("surface", "trash")
}
