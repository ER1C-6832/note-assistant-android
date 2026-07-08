package com.er1cmo.noteassistant.assistant.mcpbase

enum class McpToolStatus(val storageValue: String) {
    Success("success"),
    Failed("failed"),
    Blocked("blocked"),
    RequiresConfirmation("requires_confirmation"),
    PartialSuccess("partial_success"),
    NotImplemented("not_implemented"),
    ;

    companion object {
        fun fromStorage(value: String?): McpToolStatus = values().firstOrNull { it.storageValue == value } ?: Failed
    }
}
