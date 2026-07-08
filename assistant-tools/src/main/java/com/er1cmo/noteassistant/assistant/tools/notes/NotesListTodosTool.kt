package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesListTodosTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_todos"
    override val description: String = "列出待办便签，默认包含未完成和已完成。"
    override fun loadNotes(noteUseCases: NoteUseCases, limit: Int): Flow<List<Note>> =
        noteUseCases.listNotes().map { notes -> notes.filter { it.type == NoteType.Todo } }
}
