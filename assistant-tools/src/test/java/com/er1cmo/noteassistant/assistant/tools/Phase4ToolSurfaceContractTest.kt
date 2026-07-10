package com.er1cmo.noteassistant.assistant.tools

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4ToolSurfaceContractTest {
    @Test
    fun assistantToolsModuleBindsAllPhase4RequiredTools() {
        val module = sourceText("src/main/java/com/er1cmo/noteassistant/assistant/tools/di/AssistantToolsModule.kt")
        requiredToolClasses.forEach { className ->
            assertTrue("AssistantToolsModule must import $className", module.contains(".$className"))
            assertTrue("AssistantToolsModule must bind $className", module.contains("$className): McpTool"))
        }
    }

    @Test
    fun sourceContainsAllRequiredToolNamesIncludingPhase412Additions() {
        val source = moduleSourceText()
        requiredToolNames.forEach { toolName ->
            assertTrue("tool surface missing $toolName", source.contains("\"$toolName\""))
        }
    }

    @Test
    fun descriptorsExposeUserVisibleReferenceAndConfirmationFields() {
        descriptorRequiredFields.forEach { (relativePath, requiredFields) ->
            val source = sourceText(relativePath)
            requiredFields.forEach { field ->
                assertTrue("$relativePath descriptor must include $field", source.contains("\"$field\""))
            }
        }
    }

    @Test
    fun registryUnknownToolFailsClosed() = runBlocking {
        val registry = NoteToolRegistry(setOf(FakeTool("notes.search")))

        val result = registry.execute("notes.unknown", "{}", McpToolContext())

        assertEquals(McpToolStatus.NotImplemented.storageValue, result.status)
        assertEquals("notes.unknown", result.toolName)
        assertFalse(result.message.contains("成功"))
    }

    @Test
    fun requiresConfirmationEnvelopeCarriesIdPreviewAndAffectedIds() {
        val envelope = McpToolResult.requiresConfirmation(
            message = "将删除 1 条便签，是否确认？",
            confirmationId = "pending-phase4-14",
            toolName = "notes.delete",
            commandLogId = 24L,
            previewJson = JSONObject().put("affected_note_ids", listOf(1L)).toString(),
            affectedNoteIds = listOf(1L),
        ).toEnvelopeJsonObject()

        assertEquals("requires_confirmation", envelope.getString("status"))
        assertTrue(envelope.getBoolean("requires_confirmation"))
        assertEquals("pending-phase4-14", envelope.getString("confirmation_id"))
        assertEquals("notes.delete", envelope.getString("tool_name"))
        assertEquals(24L, envelope.getLong("command_log_id"))
        assertEquals(1L, envelope.getJSONArray("affected_note_ids").getLong(0))
        assertTrue(envelope.has("confirmation_preview"))
    }

    @Test
    fun assistantToolsDoesNotDependOnDaoRoomOrDataImpl() {
        val source = moduleSourceText()
        val forbidden = listOf(
            "androidx.room",
            "RoomDatabase",
            "com.er1cmo.noteassistant.notes.data",
            ".dao.",
            "NoteDao",
            "TagDao",
            "NoteDatabase",
            "NoteRepositoryImpl",
        )
        forbidden.forEach { token ->
            assertFalse("assistant-tools must not directly depend on $token", source.contains(token))
        }
    }

    private class FakeTool(
        override val name: String,
    ) : McpTool {
        override val description: String = "fake $name"
        override val riskLevel: McpRiskLevel = McpRiskLevel.Low
        override val descriptor: McpToolDescriptor = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = "{\"type\":\"object\",\"additionalProperties\":true}",
            riskLevel = McpRiskLevel.Low,
            mutates = false,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
            examples = listOf("example"),
        )
        override suspend fun call(argumentsJson: String): McpToolResult = McpToolResult.success("ok", toolName = name)
    }

    private fun sourceText(relativePath: String): String = Files.readString(projectRoot().resolve("assistant-tools").resolve(relativePath))

    private fun moduleSourceText(): String {
        val sourceRoot = projectRoot().resolve("assistant-tools/src/main/java")
        require(sourceRoot.isDirectory()) { "Missing source root: $sourceRoot" }
        return Files.walk(sourceRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }

    private companion object {
        val requiredToolNames = listOf(
            "notes.resolve",
            "notes.search",
            "notes.list_recent",
            "notes.get",
            "notes.list_by_tag",
            "notes.list_archived",
            "notes.list_deleted",
            "notes.list_todos",
            "notes.list_done",
            "notes.list_pinned",
            "notes.create",
            "notes.append",
            "notes.update_title",
            "notes.replace_content",
            "notes.toggle_done",
            "notes.convert_type",
            "notes.pin",
            "notes.archive",
            "notes.delete",
            "notes.restore",
            "notes.restore_revision",
            "notes.clear_done",
            "tags.create",
            "tags.search",
            "tags.list",
            "tags.rename",
            "tags.delete",
            "tags.bind",
            "ui.open_note",
            "ui.show_search",
            "ui.show_note_list",
            "ui.show_tag",
            "ui.show_archive",
            "ui.show_trash",
            "ui.show_pinned",
            "ui.show_confirmation",
            "assistant.confirm",
            "assistant.reject",
            "assistant.list_pending_confirmations",
        )

        val requiredToolClasses = listOf(
            "NotesResolveTool",
            "NotesSearchTool",
            "NotesListRecentTool",
            "NotesGetTool",
            "NotesCreateTool",
            "NotesAppendTool",
            "NotesUpdateTitleTool",
            "NotesReplaceContentTool",
            "NotesToggleDoneTool",
            "NotesConvertTypeTool",
            "NotesPinTool",
            "NotesArchiveTool",
            "NotesDeleteTool",
            "NotesRestoreTool",
            "NotesRestoreRevisionTool",
            "NotesClearDoneTool",
            "NotesListArchivedTool",
            "NotesListDeletedTool",
            "NotesListTodosTool",
            "NotesListDoneTool",
            "NotesListPinnedTool",
            "NotesListByTagTool",
            "TagsCreateTool",
            "TagsSearchTool",
            "TagsListTool",
            "TagsRenameTool",
            "TagsDeleteTool",
            "TagsBindTool",
            "UiOpenNoteTool",
            "UiShowSearchTool",
            "UiShowNoteListTool",
            "UiShowPinnedTool",
            "UiShowTagTool",
            "UiShowArchiveTool",
            "UiShowTrashTool",
            "UiShowConfirmationTool",
            "AssistantConfirmTool",
            "AssistantRejectTool",
            "AssistantListPendingConfirmationsTool",
        )

        val descriptorRequiredFields = mapOf(
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesResolveTool.kt" to listOf("query", "exact_title", "scope", "limit"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesSearchTool.kt" to listOf("query", "note_ref", "exact_title", "scope", "limit"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesGetTool.kt" to listOf("note_id", "note_ref", "note_title", "title", "query", "scope"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesDeleteTool.kt" to listOf("note_id", "note_ids", "note_ref", "query", "allow_multiple"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesToggleDoneTool.kt" to listOf("note_id", "note_ref", "done", "auto_convert_to_todo"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesConvertTypeTool.kt" to listOf("note_id", "note_ref", "target_type", "done"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/assistant/AssistantConfirmTool.kt" to listOf("confirmation_id"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/assistant/AssistantRejectTool.kt" to listOf("confirmation_id"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/ui/UiOpenNoteTool.kt" to listOf("note_id"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/ui/UiShowSearchTool.kt" to listOf("query"),
            "src/main/java/com/er1cmo/noteassistant/assistant/tools/ui/UiShowTagTool.kt" to listOf("tag_id", "tag_name"),
        )
    }
}
