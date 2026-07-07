package com.er1cmo.noteassistant.notes.domain.model

data class NoteSearchResult(
    val note: Note,
    val score: Int,
    val matchedFields: Set<NoteSearchField>,
)

enum class NoteSearchField {
    Title,
    Content,
    Tag,
}
