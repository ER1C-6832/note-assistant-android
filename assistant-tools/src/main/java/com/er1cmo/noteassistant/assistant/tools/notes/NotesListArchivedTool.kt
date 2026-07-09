package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListArchivedTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_archived"
    override val listKind: String = "archived"
    override val title: String = "列出归档便签"
    override suspend fun loadNotes(): List<Note> = archivedNotes()
}
