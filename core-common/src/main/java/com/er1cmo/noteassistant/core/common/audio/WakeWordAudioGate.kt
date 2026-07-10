package com.er1cmo.noteassistant.core.common.audio

/**
 * Runtime-facing boundary for pausing and resuming the local KWS foreground service.
 *
 * The assistant runtime depends only on this interface and never imports the wake-word
 * service implementation directly.
 */
interface WakeWordAudioGate {
    suspend fun pauseForAssistant(reason: String): Boolean
    suspend fun resumeAfterAssistant(reason: String)
}
