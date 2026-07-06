package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class ObserveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    operator fun invoke(id: Long) = repository.observeNote(id)
}
