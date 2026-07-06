package com.er1cmo.noteassistant.assistant.runtime.controller

enum class AssistantPhase { Idle, Connecting, Connected, Listening, Thinking, Speaking, Error }

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Connected,
    val statusText: String = "已连接",
)
