package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class SetNoteArchivedUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long, archived: Boolean): Boolean = repository.setArchived(id = id, archived = archived)
}
