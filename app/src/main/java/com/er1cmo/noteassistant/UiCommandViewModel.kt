package com.er1cmo.noteassistant

import androidx.lifecycle.ViewModel
import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel
class UiCommandViewModel @Inject constructor(
    uiCommandBus: UiCommandBus,
) : ViewModel() {
    val commands: SharedFlow<UiCommand> = uiCommandBus.commands
}
