package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowNoteListTool @Inject constructor(
    uiCommandBus: UiCommandBus,
) : AbstractUiSurfaceTool(uiCommandBus) {
    override val name: String = "ui.show_note_list"
    override val description: String = "显示普通便签列表。"

    override fun buildCommand(parser: ToolArgumentParser): UiCommand = UiCommand.ShowNoteList

    override fun resultPayload(parser: ToolArgumentParser): JSONObject = JSONObject()
        .put("surface", "note_list")
}
