package com.er1cmo.noteassistant.assistant.tools

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.tools.notes.NotesSearchTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {
    @Binds
    @IntoSet
    abstract fun bindNotesSearchTool(tool: NotesSearchTool): McpTool
}
