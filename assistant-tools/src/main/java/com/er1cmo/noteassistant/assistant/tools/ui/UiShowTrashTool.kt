package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowTrashTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_trash"
    override val description: String = "显示最近删除列表。"

    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowTrash

    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject()
        .put("surface", "trash")
}
