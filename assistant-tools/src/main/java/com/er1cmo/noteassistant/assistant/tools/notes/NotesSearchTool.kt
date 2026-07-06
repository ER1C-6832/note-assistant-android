package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import javax.inject.Inject

class NotesSearchTool @Inject constructor() : McpTool {
    override val name: String = "notes.search"
    override val description: String = "搜索本地便签。Phase 4 接入真实 SearchNotesUseCase。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low

    override suspend fun call(argumentsJson: String): McpToolResult = McpToolResult(
        status = "not_implemented",
        message = "Phase 4 接入真实 notes.search。",
    )
}
