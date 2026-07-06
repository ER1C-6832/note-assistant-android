package com.er1cmo.noteassistant.app.settings

data class VoiceSettings(
    val assistantEnabled: Boolean = false,
    val websocketUrl: String = "wss://example.invalid/xiaozhi",
)
