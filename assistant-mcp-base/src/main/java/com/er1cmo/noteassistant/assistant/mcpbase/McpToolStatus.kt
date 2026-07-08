package com.er1cmo.noteassistant.assistant.mcpbase

enum class McpToolStatus(val storageValue: String, val isJsonRpcError: Boolean) {
    Success("success", false),
    Failed("failed", true),
    Blocked("blocked", true),
    RequiresConfirmation("requires_confirmation", false),
    PartialSuccess("partial_success", false),
    NotImplemented("not_implemented", true),
    ;

    companion object {
        fun fromStorage(value: String?): McpToolStatus = values().firstOrNull { it.storageValue == value } ?: Failed
    }
}
