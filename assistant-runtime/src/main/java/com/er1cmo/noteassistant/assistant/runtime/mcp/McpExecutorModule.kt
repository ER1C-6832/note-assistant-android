package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlin.jvm.JvmSuppressWildcards

@Module
@InstallIn(SingletonComponent::class)
abstract class McpExecutorModule {
    @Multibinds
    abstract fun mcpToolExecutors(): Set<@JvmSuppressWildcards McpToolExecutor>
}
