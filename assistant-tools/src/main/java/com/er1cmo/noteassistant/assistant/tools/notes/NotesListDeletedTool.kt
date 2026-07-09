package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListDeletedTool @Inject constructor(
    noteUseCases: NoteUseCases,
    uiCommandBus: UiCommandBus,
) : AbstractNoteListTool(noteUseCases, uiCommandBus) {
    override val name: String = "notes.list_deleted"
    override val listKind: String = "deleted"
    override val title: String = "列出最近删除便签，并同步显示最近删除列表"
    override suspend fun loadNotes(): List<Note> = deletedNotes()
    override fun buildUiCommand(notes: List<Note>, parser: ToolArgumentParser): UiCommand = UiCommand.ShowTrash
    override fun uiEffectName(): String = "show_trash"
}
