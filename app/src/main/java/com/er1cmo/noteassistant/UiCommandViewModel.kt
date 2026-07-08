package com.er1cmo.noteassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UiCommandViewModel @Inject constructor(
    uiCommandBus: UiCommandBus,
    private val noteCommandService: NoteCommandService,
) : ViewModel() {
    val commands: SharedFlow<UiCommand> = uiCommandBus.commands

    fun confirmPendingCommand(confirmationId: String) {
        if (confirmationId.isBlank()) return
        viewModelScope.launch {
            noteCommandService.confirmPendingCommand(confirmationId)
        }
    }

    fun rejectPendingCommand(confirmationId: String) {
        if (confirmationId.isBlank()) return
        viewModelScope.launch {
            noteCommandService.rejectPendingCommand(confirmationId)
        }
    }
}
