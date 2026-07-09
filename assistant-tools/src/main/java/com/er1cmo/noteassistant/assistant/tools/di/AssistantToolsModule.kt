package com.er1cmo.noteassistant.assistant.tools.di

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.tools.NoteToolRegistry
import com.er1cmo.noteassistant.assistant.tools.assistant.AssistantConfirmTool
import com.er1cmo.noteassistant.assistant.tools.assistant.AssistantListPendingConfirmationsTool
import com.er1cmo.noteassistant.assistant.tools.assistant.AssistantRejectTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesAppendTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesArchiveTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesClearDoneTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesCreateTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesDeleteTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesGetTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListArchivedTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListByTagTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListDeletedTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListDoneTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListPinnedTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListRecentTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListTodosTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesPinTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesReplaceContentTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesRestoreRevisionTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesRestoreTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesSearchTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesToggleDoneTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesUpdateTitleTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsBindTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsCreateTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsDeleteTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsListTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsRenameTool
import com.er1cmo.noteassistant.assistant.tools.tags.TagsSearchTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiOpenNoteTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowArchiveTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowConfirmationTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowNoteListTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowSearchTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowTagTool
import com.er1cmo.noteassistant.assistant.tools.ui.UiShowTrashTool
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

    @Binds @IntoSet abstract fun bindNotesSearchTool(tool: NotesSearchTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListRecentTool(tool: NotesListRecentTool): McpTool
    @Binds @IntoSet abstract fun bindNotesGetTool(tool: NotesGetTool): McpTool
    @Binds @IntoSet abstract fun bindNotesCreateTool(tool: NotesCreateTool): McpTool
    @Binds @IntoSet abstract fun bindNotesAppendTool(tool: NotesAppendTool): McpTool
    @Binds @IntoSet abstract fun bindNotesDeleteTool(tool: NotesDeleteTool): McpTool
    @Binds @IntoSet abstract fun bindNotesUpdateTitleTool(tool: NotesUpdateTitleTool): McpTool
    @Binds @IntoSet abstract fun bindNotesToggleDoneTool(tool: NotesToggleDoneTool): McpTool
    @Binds @IntoSet abstract fun bindNotesPinTool(tool: NotesPinTool): McpTool
    @Binds @IntoSet abstract fun bindNotesReplaceContentTool(tool: NotesReplaceContentTool): McpTool
    @Binds @IntoSet abstract fun bindNotesRestoreRevisionTool(tool: NotesRestoreRevisionTool): McpTool

    @Binds @IntoSet abstract fun bindNotesArchiveTool(tool: NotesArchiveTool): McpTool
    @Binds @IntoSet abstract fun bindNotesRestoreTool(tool: NotesRestoreTool): McpTool
    @Binds @IntoSet abstract fun bindNotesClearDoneTool(tool: NotesClearDoneTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListArchivedTool(tool: NotesListArchivedTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListDeletedTool(tool: NotesListDeletedTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListTodosTool(tool: NotesListTodosTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListDoneTool(tool: NotesListDoneTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListPinnedTool(tool: NotesListPinnedTool): McpTool
    @Binds @IntoSet abstract fun bindNotesListByTagTool(tool: NotesListByTagTool): McpTool

    @Binds @IntoSet abstract fun bindUiOpenNoteTool(tool: UiOpenNoteTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowConfirmationTool(tool: UiShowConfirmationTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowSearchTool(tool: UiShowSearchTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowNoteListTool(tool: UiShowNoteListTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowTagTool(tool: UiShowTagTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowArchiveTool(tool: UiShowArchiveTool): McpTool
    @Binds @IntoSet abstract fun bindUiShowTrashTool(tool: UiShowTrashTool): McpTool

    @Binds @IntoSet abstract fun bindTagsSearchTool(tool: TagsSearchTool): McpTool
    @Binds @IntoSet abstract fun bindTagsBindTool(tool: TagsBindTool): McpTool
    @Binds @IntoSet abstract fun bindTagsDeleteTool(tool: TagsDeleteTool): McpTool
    @Binds @IntoSet abstract fun bindTagsCreateTool(tool: TagsCreateTool): McpTool
    @Binds @IntoSet abstract fun bindTagsRenameTool(tool: TagsRenameTool): McpTool
    @Binds @IntoSet abstract fun bindTagsListTool(tool: TagsListTool): McpTool

    @Binds @IntoSet abstract fun bindAssistantConfirmTool(tool: AssistantConfirmTool): McpTool
    @Binds @IntoSet abstract fun bindAssistantRejectTool(tool: AssistantRejectTool): McpTool
    @Binds @IntoSet abstract fun bindAssistantListPendingConfirmationsTool(tool: AssistantListPendingConfirmationsTool): McpTool
}
