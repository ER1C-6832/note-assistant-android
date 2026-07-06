package com.er1cmo.noteassistant.assistant.bridge

sealed interface UiCommand {
    data class OpenNote(val noteId: Long) : UiCommand
    data class ShowMessage(val message: String) : UiCommand
    data class ShowConfirmation(val confirmationId: String, val title: String, val message: String) : UiCommand
}
