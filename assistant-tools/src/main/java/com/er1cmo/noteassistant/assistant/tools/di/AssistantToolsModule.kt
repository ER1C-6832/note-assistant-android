package com.er1cmo.noteassistant.assistant.tools.di

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.tools.NoteToolRegistry
import com.er1cmo.noteassistant.assistant.tools.notes.NotesAppendTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesCreateTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesDeleteTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesGetTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesListRecentTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesSearchTool
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
}
