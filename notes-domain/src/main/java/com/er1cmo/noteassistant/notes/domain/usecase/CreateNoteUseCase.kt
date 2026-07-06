package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class CreateNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(
        title: String,
        content: String,
        type: NoteType,
        color: String?,
    ): Long = repository.createNote(
        title = title,
        content = content,
        type = type,
        color = color,
    )
}
