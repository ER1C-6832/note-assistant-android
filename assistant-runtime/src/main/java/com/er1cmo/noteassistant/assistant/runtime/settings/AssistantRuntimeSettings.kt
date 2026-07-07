package com.er1cmo.noteassistant.assistant.runtime.settings

data class AssistantRuntimeSettings(
    val assistantEnabled: Boolean = false,
    val websocketUrl: String = DEFAULT_WEBSOCKET_URL,
    val otaUrl: String = DEFAULT_OTA_URL,
    val activationMode: ActivationMode = ActivationMode.Fake,
) {
    enum class ActivationMode {
        Fake,
        Real,
    }

    companion object {
        const val DEFAULT_WEBSOCKET_URL = "wss://example.invalid/xiaozhi"
        const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
    }
}
