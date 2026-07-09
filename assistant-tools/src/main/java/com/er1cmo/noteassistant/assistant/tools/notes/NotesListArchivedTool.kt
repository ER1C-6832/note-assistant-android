package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListArchivedTool @Inject constructor(
    noteUseCases: NoteUseCases,
    uiCommandBus: UiCommandBus,
) : AbstractNoteListTool(noteUseCases, uiCommandBus) {
    override val name: String = "notes.list_archived"
    override val listKind: String = "archived"
    override val title: String = "列出归档便签，并同步显示归档列表"
    override suspend fun loadNotes(): List<Note> = archivedNotes()
    override fun buildUiCommand(notes: List<Note>, parser: ToolArgumentParser): UiCommand = UiCommand.ShowArchive
    override fun uiEffectName(): String = "show_archive"
}
