package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListDeletedTool @Inject constructor(
    noteUseCases: NoteUseCases,
) : AbstractNoteListTool(noteUseCases) {
    override val name: String = "notes.list_deleted"
    override val listKind: String = "deleted"
    override val title: String = "列出最近删除便签"
    override suspend fun loadNotes(): List<Note> = deletedNotes()
}
