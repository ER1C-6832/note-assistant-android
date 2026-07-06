package com.er1cmo.noteassistant.notes.domain.usecase

import javax.inject.Inject

data class NoteUseCases @Inject constructor(
    val listNotes: ListNotesUseCase,
    val seedDemoNotes: SeedDemoNotesUseCase,
)
