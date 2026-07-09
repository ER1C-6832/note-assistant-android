package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowTagTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_tag"
    override val description: String = "显示指定标签相关便签。"
    override val inputSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "tag_id": { "type": "integer" },
            "tag_name": { "type": "string" },
            "tag": { "type": "string" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowTag(
        tagId = parser.optionalLong("tag_id")?.takeIf { it > 0L },
        tagName = parser.optionalString("tag_name", parser.optionalString("tag", "")),
    )

    override fun resultPayload(parser: ToolArgumentParser): JSONObject {
        val tagId = parser.optionalLong("tag_id")?.takeIf { it > 0L }
        val tagName = parser.optionalString("tag_name", parser.optionalString("tag", ""))
        return JSONObject()
            .put("tag_id", tagId ?: JSONObject.NULL)
            .put("tag_name", tagName)
    }
}
