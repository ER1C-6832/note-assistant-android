package com.er1cmo.noteassistant.notes.domain.command

enum class CommandSource(val storageValue: String) {
    Manual("manual"),
    LocalToolSimulator("local_tool_simulator"),
    Voice("voice"),
    Wakeword("wakeword"),
    Import("import"),
    System("system"),
    ;

    companion object {
        fun fromStorage(value: String?): CommandSource = values().firstOrNull { it.storageValue == value } ?: System
    }
}

enum class RiskLevel(val storageValue: String) {
    Low("low"),
    Medium("medium"),
    High("high"),
    ;

    companion object {
        fun fromStorage(value: String?): RiskLevel = values().firstOrNull { it.storageValue == value } ?: High
    }
}

enum class ConfirmationStatus(val storageValue: String) {
    NotRequired("not_required"),
    Pending("pending"),
    Confirmed("confirmed"),
    Rejected("rejected"),
    Expired("expired"),
    ;

    companion object {
        fun fromStorage(value: String?): ConfirmationStatus = values().firstOrNull { it.storageValue == value } ?: Expired
    }
}

enum class CommandStatus(val storageValue: String) {
    Success("success"),
    Failed("failed"),
    Blocked("blocked"),
    RequiresConfirmation("requires_confirmation"),
    PartialSuccess("partial_success"),
    ;

    companion object {
        fun fromStorage(value: String?): CommandStatus = values().firstOrNull { it.storageValue == value } ?: Failed
    }
}

enum class CommandErrorCode(val storageValue: String) {
    InvalidJson("invalid_json"),
    ValidationError("validation_error"),
    NotFound("not_found"),
    AlreadyDeleted("already_deleted"),
    AlreadyArchived("already_archived"),
    Conflict("conflict"),
    RequiresConfirmation("requires_confirmation"),
    ConfirmationExpired("confirmation_expired"),
    ConfirmationRejected("confirmation_rejected"),
    StorageError("storage_error"),
    UnsupportedTool("unsupported_tool"),
    PartialFailure("partial_failure"),
    ;

    companion object {
        fun fromStorage(value: String?): CommandErrorCode? = values().firstOrNull { it.storageValue == value }
    }
}

enum class ToolName(val storageValue: String) {
    NotesCreate("notes.create"),
    NotesSearch("notes.search"),
    NotesListRecent("notes.list_recent"),
    NotesAppend("notes.append"),
    NotesUpdateTitle("notes.update_title"),
    NotesReplaceContent("notes.replace_content"),
    NotesToggleDone("notes.toggle_done"),
    NotesPin("notes.pin"),
    NotesArchive("notes.archive"),
    NotesDelete("notes.delete"),
    NotesRestore("notes.restore"),
    NotesRestoreRevision("notes.restore_revision"),
    TagsSearch("tags.search"),
    TagsBind("tags.bind"),
    TagsDelete("tags.delete"),
    TagsCreate("tags.create"),
    TagsRename("tags.rename"),
    UiOpenNote("ui.open_note"),
    Unsupported("unsupported"),
    ;

    companion object {
        fun fromStorage(value: String?): ToolName = values().firstOrNull { it.storageValue == value } ?: Unsupported
    }
}

enum class TagBindMode(val storageValue: String) {
    Add("add"),
    Remove("remove"),
    Replace("replace"),
    ;

    companion object {
        fun fromStorage(value: String?): TagBindMode? = values().firstOrNull { it.storageValue == value }
    }
}
