package com.er1cmo.noteassistant.assistant.runtime.network

data class XiaozhiConnectionConfig(
    val websocketUrl: String,
    val websocketToken: String,
    val deviceId: String,
    val clientId: String,
)
