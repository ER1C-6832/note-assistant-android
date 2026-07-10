package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListDoneTool @Inject constructor(
    noteUseCases: NoteUseCases,
    uiCommandBus: UiCommandBus,
) : AbstractNoteListTool(noteUseCases, uiCommandBus) {
    override val name: String = "notes.list_done"
    override val listKind: String = "done"
    override val title: String = "列出已完成待办，并同步显示已完成列表"
    override suspend fun loadNotes(): List<Note> = activeNotes().filter { it.type == NoteType.Todo && it.isDone }
    override fun buildUiCommand(notes: List<Note>, parser: ToolArgumentParser): UiCommand = UiCommand.ShowDone
    override fun uiEffectName(): String = "show_done"
    override fun spokenLabel(): String = "已完成待办"
}
