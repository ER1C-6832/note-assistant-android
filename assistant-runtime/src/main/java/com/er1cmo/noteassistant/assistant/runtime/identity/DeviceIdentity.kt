package com.er1cmo.noteassistant.assistant.runtime.identity

data class DeviceIdentity(
    val clientId: String,
    val deviceId: String,
    val serialNumber: String,
    val hmacKey: String,
) {
    val displayDeviceId: String
        get() = deviceId.ifBlank { "未生成" }
}
