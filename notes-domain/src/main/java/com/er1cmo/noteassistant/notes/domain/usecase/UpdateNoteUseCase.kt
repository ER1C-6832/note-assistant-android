package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(
        id: Long,
        title: String,
        content: String,
        type: NoteType,
        color: String?,
        tagText: String,
    ) = repository.updateNote(
        id = id,
        title = title,
        content = content,
        type = type,
        color = color,
        tagText = tagText,
    )
}
