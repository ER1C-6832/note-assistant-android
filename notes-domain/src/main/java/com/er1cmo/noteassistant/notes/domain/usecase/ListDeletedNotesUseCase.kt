package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class ListDeletedNotesUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    operator fun invoke() = repository.observeDeletedNotes()
}
