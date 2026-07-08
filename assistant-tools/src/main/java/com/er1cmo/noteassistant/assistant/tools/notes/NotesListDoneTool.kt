package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesListDoneTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_done"
    override val description: String = "列出已完成的待办便签。"
    override fun loadNotes(noteUseCases: NoteUseCases, limit: Int): Flow<List<Note>> =
        noteUseCases.listNotes().map { notes -> notes.filter { it.type == NoteType.Todo && it.isDone } }
}
