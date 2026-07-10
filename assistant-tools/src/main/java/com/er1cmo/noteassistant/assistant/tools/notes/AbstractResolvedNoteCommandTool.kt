package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import org.json.JSONObject

abstract class AbstractResolvedNoteCommandTool(
    private val commandService: NoteCommandService,
    private val resolver: NoteReferenceResolver,
    private val targetScope: NoteResolveScope = NoteResolveScope.ActiveAndArchived,
    private val supportsMultipleTargets: Boolean = false,
    private val referenceFields: List<String> = DEFAULT_NOTE_REFERENCE_FIELDS,
    private val exactTitleFields: List<String> = listOf("exact_title", "note_title", "target_title"),
) : McpTool {
    final override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    final override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        return when (
            val prepared = prepareResolvedNoteArguments(
                toolName = name,
                argumentsJson = argumentsJson,
                context = context,
                risk = riskLevel,
                resolver = resolver,
                scope = targetScope,
                supportsMultiple = supportsMultipleTargets,
                referenceFields = referenceFields,
                exactTitleFields = exactTitleFields,
            )
        ) {
            is PreparedNoteArguments.Failed -> prepared.result
            is PreparedNoteArguments.Ready -> {
                val normalizedArguments = runCatching {
                    normalizeResolvedArguments(JSONObject(prepared.argumentsJson)).toString()
                }.getOrElse { error ->
                    return McpToolResult.failed(
                        message = error.message ?: "工具参数校验失败",
                        toolName = name,
                        argumentsJson = prepared.argumentsJson,
                        errorCode = McpToolResult.ERROR_VALIDATION,
                        risk = riskLevel,
                    )
                }
                commandService.execute(
                    toolName = name,
                    argumentsJson = normalizedArguments,
                    source = context.toCommandSource(),
                ).toMcpToolResult(
                    toolName = name,
                    argumentsJson = normalizedArguments,
                ).withResolvedNoteTargets(prepared.notes)
            }
        }
    }

    protected open fun normalizeResolvedArguments(arguments: JSONObject): JSONObject = arguments
}
