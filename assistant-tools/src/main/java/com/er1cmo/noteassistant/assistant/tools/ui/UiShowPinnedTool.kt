package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowPinnedTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_pinned"
    override val description: String = "显示置顶便签列表。"
    override val inputSchemaJson: String = """
        {
          "type": "object",
          "properties": {},
          "additionalProperties": false
        }
    """.trimIndent()

    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowPinned

    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject()
        .put("surface", "pinned")
}
