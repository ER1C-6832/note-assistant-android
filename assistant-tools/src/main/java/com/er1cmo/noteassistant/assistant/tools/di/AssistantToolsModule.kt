package com.er1cmo.noteassistant.assistant.tools.di

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.tools.NoteToolRegistry
import com.er1cmo.noteassistant.assistant.tools.assistant.AssistantConfirmTool
import com.er1cmo.noteassistant.assistant.tools.assistant.AssistantRejectTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesAppendTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesCreateTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesDeleteTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesGetTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListRecentTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesPinTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesReplaceContentTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesRestoreRevisionTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesSearchTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesToggleDoneTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesUpdateTitleTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsBindTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsDeleteTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsSearchTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiOpenNoteTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowConfirmationTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantToolsModule {
    @Binds
    @IntoSet
    abstract fun bindNoteToolExecutor(registry: NoteToolRegistry): McpToolExecutor

    @Binds
    @IntoSet
    abstract fun bindNotesSearchTool(tool: NotesSearchTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesListRecentTool(tool: NotesListRecentTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesGetTool(tool: NotesGetTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesCreateTool(tool: NotesCreateTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesAppendTool(tool: NotesAppendTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesDeleteTool(tool: NotesDeleteTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesUpdateTitleTool(tool: NotesUpdateTitleTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesToggleDoneTool(tool: NotesToggleDoneTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesPinTool(tool: NotesPinTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesReplaceContentTool(tool: NotesReplaceContentTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindNotesRestoreRevisionTool(tool: NotesRestoreRevisionTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindUiOpenNoteTool(tool: UiOpenNoteTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindUiShowConfirmationTool(tool: UiShowConfirmationTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindTagsSearchTool(tool: TagsSearchTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindTagsBindTool(tool: TagsBindTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindTagsDeleteTool(tool: TagsDeleteTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindAssistantConfirmTool(tool: AssistantConfirmTool): McpTool

    @Binds
    @IntoSet
    abstract fun bindAssistantRejectTool(tool: AssistantRejectTool): McpTool
}
