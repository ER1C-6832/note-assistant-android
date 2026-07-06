package com.er1cmo.noteassistant.assistant.bridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiCommandBus @Inject constructor() {
    private val _commands = MutableSharedFlow<UiCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<UiCommand> = _commands.asSharedFlow()

    fun emit(command: UiCommand) {
        _commands.tryEmit(command)
    }
}
