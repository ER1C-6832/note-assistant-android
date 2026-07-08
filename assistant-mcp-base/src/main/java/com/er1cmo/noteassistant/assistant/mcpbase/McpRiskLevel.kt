package com.er1cmo.noteassistant.assistant.mcpbase

enum class McpRiskLevel(val storageValue: String) {
    Low("low"),
    Medium("medium"),
    High("high"),
    ;

    companion object {
        fun fromStorage(value: String?): McpRiskLevel = values().firstOrNull { it.storageValue == value } ?: High
    }
}
