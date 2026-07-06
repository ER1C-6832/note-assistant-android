package com.er1cmo.noteassistant.notes.domain.usecase

import javax.inject.Inject

data class NoteUseCases @Inject constructor(
    val listNotes: ListNotesUseCase,
    val listDeletedNotes: ListDeletedNotesUseCase,
    val getNote: GetNoteUseCase,
    val createNote: CreateNoteUseCase,
    val updateNote: UpdateNoteUseCase,
    val toggleTodoDone: ToggleTodoDoneUseCase,
    val setNotePinned: SetNotePinnedUseCase,
    val softDeleteNote: SoftDeleteNoteUseCase,
    val restoreDeletedNote: RestoreDeletedNoteUseCase,
    val seedDemoNotes: SeedDemoNotesUseCase,
)
