package com.er1cmo.noteassistant.assistant.tools

/**
 * Legacy placeholder kept only to avoid stale imports or package references.
 *
 * Phase4 tool bindings live in:
 * assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/di/AssistantToolsModule.kt
 *
 * Do not add Hilt @Module bindings here. Binding the same McpTool in both this
 * file and AssistantToolsModule causes duplicate Dagger set contributions.
 */
@Deprecated(
    message = "Use com.er1cmo.noteassistant.assistant.tools.di.AssistantToolsModule for Hilt bindings.",
    level = DeprecationLevel.WARNING,
)
object ToolsModule
