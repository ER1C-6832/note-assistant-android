package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListDoneTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_done"
    override val listKind: String = "done"
    override val title: String = "列出已完成待办"
    override suspend fun loadNotes(): List<Note> = activeNotes().filter { it.type == com.er1cmo.noteassistant.notes.domain.model.NoteType.Todo && it.isDone }
}
