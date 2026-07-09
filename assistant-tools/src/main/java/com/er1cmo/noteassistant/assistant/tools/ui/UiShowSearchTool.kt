package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowSearchTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_search"
    override val description: String = "显示搜索页或搜索结果。"
    override val inputSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "query": { "type": "string" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowSearch(
        query = parser.optionalString("query", ""),
    )

    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject()
        .put("query", parser.optionalString("query", ""))
}
