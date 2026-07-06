package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class GetNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long) = repository.getNote(id)
}
