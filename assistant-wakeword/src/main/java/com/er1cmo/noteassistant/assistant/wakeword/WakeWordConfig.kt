package com.er1cmo.noteassistant.assistant.wakeword

data class WakeWordConfig(
    val enabled: Boolean = false,
    val preset: String = "小智小智",
    val sensitivity: String = "standard",
)
