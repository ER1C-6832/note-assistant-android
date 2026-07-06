package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import javax.inject.Inject

class CreateTagUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(name: String): Boolean = repository.createTag(name)
}
