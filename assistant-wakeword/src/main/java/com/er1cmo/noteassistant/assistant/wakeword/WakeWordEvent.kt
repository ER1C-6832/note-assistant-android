package com.er1cmo.noteassistant.assistant.wakeword

sealed class WakeWordEvent {
    data class Detected(
        val keyword: String,
        val rawKeyword: String,
        val source: String,
        val latencyMs: Long,
        val keywordsScore: Float,
        val keywordsThreshold: Float,
        val cooldownMs: Long,
        val detectedAt: Long = System.currentTimeMillis(),
    ) : WakeWordEvent()

    data class Status(
        val message: String,
        val state: String = "status",
        val keyword: String = "",
        val latencyMs: Long? = null,
    ) : WakeWordEvent()

    data class Error(
        val message: String,
        val state: String = "error",
    ) : WakeWordEvent()
}
