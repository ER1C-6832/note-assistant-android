# Phase4-03 Buildfix 01 - Duplicate Tool Binding

## Problem

`:app:hiltJavaCompileDebug` failed because `NotesSearchTool` was contributed twice into the same Dagger `Set<McpTool>`:

- `com.er1cmo.noteassistant.assistant.tools.di.AssistantToolsModule.bindNotesSearchTool`
- `com.er1cmo.noteassistant.assistant.tools.ToolsModule.bindNotesSearchTool`

## Fix

`assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/ToolsModule.kt` is now a legacy no-op placeholder without `@Module`, `@Binds`, or `@IntoSet`.

The single source of Phase4 tool bindings is:

`assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/di/AssistantToolsModule.kt`

## Boundary

This buildfix does not change Phase4 tool behavior.

- `assistant-runtime` still delegates to `McpToolExecutor`.
- Real tools remain in `assistant-tools`.
- `assistant-runtime` still must not depend on `NoteCommandService`, `notes-data`, Room DAO, or repository implementations.
