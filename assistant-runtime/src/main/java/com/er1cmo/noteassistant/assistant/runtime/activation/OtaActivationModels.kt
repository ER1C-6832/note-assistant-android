package com.er1cmo.noteassistant.assistant.runtime.activation

data class ActivationInfo(
    val code: String,
    val challenge: String,
    val message: String,
)

data class OtaResponse(
    val websocketUrl: String?,
    val websocketToken: String?,
    val activation: ActivationInfo?,
    val rawJson: String,
    val redactedJson: String,
)

data class OtaActivationOutcome(
    val state: OtaActivationState,
    val message: String,
    val websocketUrl: String? = null,
    val activationCode: String? = null,
)

enum class OtaActivationState {
    Activated,
    Required,
    Failed,
}
