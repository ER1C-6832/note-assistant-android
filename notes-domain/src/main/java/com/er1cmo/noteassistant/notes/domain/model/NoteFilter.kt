package com.er1cmo.noteassistant.notes.domain.model

data class NoteFilter(
    val query: String = "",
    val tagIds: Set<Long> = emptySet(),
    val includeDone: Boolean = true,
    val includeArchived: Boolean = false,
    val includeDeleted: Boolean = false,
)
