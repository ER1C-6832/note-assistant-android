package com.er1cmo.noteassistant.assistant.bridge

sealed interface UiCommand {
    data class OpenNote(val noteId: Long) : UiCommand
    data class ShowMessage(val message: String) : UiCommand
    data class ShowConfirmation(val confirmationId: String, val title: String, val message: String) : UiCommand
    data class ShowSearch(val query: String) : UiCommand
    data object ShowNoteList : UiCommand
    data class ShowTag(val tagId: Long?, val tagName: String) : UiCommand
    data object ShowArchive : UiCommand
    data object ShowTrash : UiCommand
    data object ShowPinned : UiCommand
    data object ShowTodos : UiCommand
    data object ShowDone : UiCommand
}
