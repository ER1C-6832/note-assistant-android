package com.er1cmo.noteassistant.assistant.runtime.toolcall

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ToolCallUiStatus {
    Idle,
    Running,
    Success,
    Failed,
    RequiresConfirmation,
    PartialSuccess,
    Blocked,
    NotImplemented,
}

data class ToolCallUiState(
    val visible: Boolean = false,
    val status: ToolCallUiStatus = ToolCallUiStatus.Idle,
    val toolName: String? = null,
    val message: String = "等待工具调用",
    val detail: String? = null,
    val commandLogId: Long? = null,
    val confirmationId: String? = null,
    val confirmationSummary: String? = null,
    val errorCode: String? = null,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val updatedAtMillis: Long = 0L,
) {
    val isTerminal: Boolean
        get() = status != ToolCallUiStatus.Running
}

sealed interface ToolCallEvent {
    val state: ToolCallUiState

    data class Started(
        override val state: ToolCallUiState,
    ) : ToolCallEvent

    data class Completed(
        override val state: ToolCallUiState,
    ) : ToolCallEvent

    data class Cleared(
        override val state: ToolCallUiState,
    ) : ToolCallEvent
}

@Singleton
class ToolCallEventStore @Inject constructor() {
    private val mutableState = MutableStateFlow(ToolCallUiState())
    private val mutableEvents = MutableSharedFlow<ToolCallEvent>(extraBufferCapacity = 32)

    val state: StateFlow<ToolCallUiState> = mutableState.asStateFlow()
    val events: SharedFlow<ToolCallEvent> = mutableEvents.asSharedFlow()

    fun markRunning(
        toolName: String,
        argumentsJson: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val next = ToolCallUiState(
            visible = true,
            status = ToolCallUiStatus.Running,
            toolName = toolName,
            message = runningMessage(toolName),
            detail = argumentsJson?.takeIf { it.isNotBlank() }?.let { compactJsonPreview(it) },
            startedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        mutableState.value = next
        mutableEvents.tryEmit(ToolCallEvent.Started(next))
    }

    fun markCompleted(
        result: McpToolResult,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val toolName = result.toolName.orEmpty().ifBlank { mutableState.value.toolName.orEmpty() }.ifBlank { null }
        val next = ToolCallUiState(
            visible = true,
            status = result.statusEnum.toUiStatus(),
            toolName = toolName,
            message = completedMessage(toolName, result),
            detail = detailMessage(result),
            commandLogId = result.commandLogId,
            confirmationId = result.confirmationId,
            confirmationSummary = result.confirmationSummary,
            errorCode = result.errorCode,
            startedAtMillis = mutableState.value.startedAtMillis,
            completedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        mutableState.value = next
        mutableEvents.tryEmit(ToolCallEvent.Completed(next))
    }

    fun markFailed(
        toolName: String?,
        message: String,
        errorCode: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val next = ToolCallUiState(
            visible = true,
            status = ToolCallUiStatus.Failed,
            toolName = toolName,
            message = "工具执行失败",
            detail = sanitizeMessage(message),
            errorCode = errorCode,
            completedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        mutableState.value = next
        mutableEvents.tryEmit(ToolCallEvent.Completed(next))
    }

    fun clear(nowMillis: Long = System.currentTimeMillis()) {
        val next = ToolCallUiState(updatedAtMillis = nowMillis)
        mutableState.value = next
        mutableEvents.tryEmit(ToolCallEvent.Cleared(next))
    }
}

private fun McpToolStatus.toUiStatus(): ToolCallUiStatus = when (this) {
    McpToolStatus.Success -> ToolCallUiStatus.Success
    McpToolStatus.Failed -> ToolCallUiStatus.Failed
    McpToolStatus.Blocked -> ToolCallUiStatus.Blocked
    McpToolStatus.RequiresConfirmation -> ToolCallUiStatus.RequiresConfirmation
    McpToolStatus.PartialSuccess -> ToolCallUiStatus.PartialSuccess
    McpToolStatus.NotImplemented -> ToolCallUiStatus.NotImplemented
}

private fun runningMessage(toolName: String): String = when (toolName) {
    "notes.create" -> "正在创建便签"
    "notes.append" -> "正在追加内容"
    "notes.update_title" -> "正在修改标题"
    "notes.toggle_done" -> "正在更新完成状态"
    "notes.pin" -> "正在更新置顶状态"
    "notes.archive" -> "正在归档便签"
    "notes.restore" -> "正在恢复便签"
    "notes.delete" -> "正在准备删除确认"
    "notes.replace_content" -> "正在准备覆盖确认"
    "notes.restore_revision" -> "正在准备恢复版本确认"
    "notes.clear_done" -> "正在准备清理确认"
    "notes.search" -> "正在搜索便签"
    "notes.list_recent" -> "正在列出最近便签"
    "notes.list_archived" -> "正在列出归档便签"
    "notes.list_deleted" -> "正在列出最近删除"
    "notes.list_todos" -> "正在列出待办"
    "notes.list_done" -> "正在列出已完成"
    "notes.list_pinned" -> "正在列出置顶便签"
    "notes.list_by_tag" -> "正在按标签列出便签"
    "ui.open_note" -> "正在打开便签"
    "ui.show_search" -> "正在切换搜索视图"
    "ui.show_note_list" -> "正在显示便签列表"
    "ui.show_tag" -> "正在切换标签视图"
    "ui.show_archive" -> "正在显示归档列表"
    "ui.show_trash" -> "正在显示回收站"
    "tags.create" -> "正在创建标签"
    "tags.rename" -> "正在重命名标签"
    "tags.list" -> "正在列出标签"
    "tags.search" -> "正在搜索标签"
    "tags.bind" -> "正在更新标签绑定"
    "tags.delete" -> "正在准备删除标签确认"
    "assistant.confirm" -> "正在确认操作"
    "assistant.reject" -> "正在拒绝操作"
    "assistant.list_pending_confirmations" -> "正在列出待确认操作"
    else -> "正在执行工具"
}

private fun completedMessage(toolName: String?, result: McpToolResult): String {
    return when (result.statusEnum) {
        McpToolStatus.Success -> successMessage(toolName)
        McpToolStatus.RequiresConfirmation -> confirmationMessage(toolName, result)
        McpToolStatus.PartialSuccess -> "部分完成"
        McpToolStatus.Blocked -> "工具已被安全阻断"
        McpToolStatus.NotImplemented -> "工具尚未支持"
        McpToolStatus.Failed -> "工具执行失败"
    }
}

private fun successMessage(toolName: String?): String = when (toolName) {
    "notes.create" -> "已创建便签"
    "notes.append" -> "已追加内容"
    "notes.update_title" -> "已修改标题"
    "notes.toggle_done" -> "已更新完成状态"
    "notes.pin" -> "已更新置顶状态"
    "notes.archive" -> "已归档便签"
    "notes.restore" -> "已恢复便签"
    "notes.search" -> "已完成搜索"
    "notes.list_recent" -> "已列出最近便签"
    "notes.list_archived" -> "已列出归档便签"
    "notes.list_deleted" -> "已列出最近删除"
    "notes.list_todos" -> "已列出待办"
    "notes.list_done" -> "已列出已完成"
    "notes.list_pinned" -> "已列出置顶便签"
    "notes.list_by_tag" -> "已按标签列出便签"
    "notes.get" -> "已读取便签"
    "ui.open_note" -> "已打开便签"
    "ui.show_search" -> "已显示搜索视图"
    "ui.show_note_list" -> "已显示便签列表"
    "ui.show_tag" -> "已显示标签视图"
    "ui.show_archive" -> "已显示归档列表"
    "ui.show_trash" -> "已显示回收站"
    "tags.create" -> "已创建标签"
    "tags.rename" -> "已重命名标签"
    "tags.list" -> "已列出标签"
    "tags.search" -> "已完成标签搜索"
    "tags.bind" -> "已更新标签"
    "assistant.confirm" -> "已确认操作"
    "assistant.reject" -> "已拒绝操作"
    "assistant.list_pending_confirmations" -> "已列出待确认操作"
    else -> "工具执行完成"
}

private fun confirmationMessage(toolName: String?, result: McpToolResult): String {
    return when (toolName) {
        "notes.delete" -> "需要确认删除"
        "notes.replace_content" -> "需要确认覆盖正文"
        "notes.restore_revision" -> "需要确认恢复版本"
        "notes.clear_done" -> "需要确认清理已完成"
        "tags.bind" -> "需要确认替换标签"
        "tags.delete" -> "需要确认删除标签"
        "tags.rename" -> "需要确认重命名标签"
        else -> result.confirmationSummary?.let { "需要确认：$it" } ?: "需要确认操作"
    }
}

private fun detailMessage(result: McpToolResult): String? {
    val base = when (result.statusEnum) {
        McpToolStatus.Success,
        McpToolStatus.PartialSuccess -> result.message
        McpToolStatus.RequiresConfirmation -> result.confirmationSummary ?: result.message
        McpToolStatus.Failed,
        McpToolStatus.Blocked,
        McpToolStatus.NotImplemented -> result.message
    }
    val extras = buildList {
        result.confirmationId?.let { add("confirmation_id=$it") }
        result.commandLogId?.let { add("log_id=$it") }
        result.errorCode?.let { add("error=$it") }
    }
    return listOf(sanitizeMessage(base), extras.joinToString("  "))
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .ifBlank { null }
}

private fun sanitizeMessage(message: String): String {
    return message
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.replace("file://", "")
        ?.replace("Exception", "错误")
        ?.take(120)
        .orEmpty()
}

private fun compactJsonPreview(json: String): String {
    val compact = runCatching { JSONObject(json).toString() }.getOrDefault(json.trim())
    return compact.take(160)
}
