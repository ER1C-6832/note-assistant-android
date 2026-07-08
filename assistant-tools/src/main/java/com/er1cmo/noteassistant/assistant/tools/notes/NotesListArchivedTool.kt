package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class NotesListArchivedTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_archived"
    override val description: String = "列出已归档便签。"
    override fun loadNotes(noteUseCases: NoteUseCases, limit: Int): Flow<List<Note>> = noteUseCases.listArchivedNotes()
}
