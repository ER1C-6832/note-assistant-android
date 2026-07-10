package com.er1cmo.noteassistant.notes.ui.list

sealed interface NoteListExternalCommand {
    val sequence: Long

    data class ShowSearch(
        val query: String,
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowTag(
        val tagId: Long?,
        val tagName: String,
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowArchive(
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowTrash(
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowPinned(
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowTodos(
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowDone(
        override val sequence: Long,
    ) : NoteListExternalCommand

    data class ShowNoteList(
        override val sequence: Long,
    ) : NoteListExternalCommand
}
