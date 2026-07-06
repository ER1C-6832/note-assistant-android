package com.er1cmo.noteassistant.assistant.runtime.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantController @Inject constructor() {
    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    fun markListening() {
        _state.value = AssistantState(AssistantPhase.Listening, "正在聆听")
    }

    fun markConnected() {
        _state.value = AssistantState(AssistantPhase.Connected, "已连接")
    }
}
