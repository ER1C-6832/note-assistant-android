# Phase4-00 Cleanup and Interface Shaping Report

## Status

This overlay prepares the repository for Phase4 MCP notes tools.

Phase4-00 does not enable real notes mutation from MCP yet. It cleans the model boundary, moves reusable MCP descriptor/result/executor contracts into `assistant-mcp-base`, and keeps Phase3 runtime fail-closed until the real executor wiring stage.

## What changed

### 1. Duplicate model cleanup helper

Added cleanup scripts:

- `scripts/phase4-00-cleanup.ps1`
- `scripts/phase4-00-cleanup.sh`

The scripts delete these stale duplicate files if present:

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/model/command/PendingConfirmation.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/model/command/NoteRevision.kt`
- `assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/controller/AssistantState.kt`

The canonical domain models remain:

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/model/PendingConfirmation.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/model/NoteRevision.kt`
- `assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/state/AssistantState.kt`

### 2. MCP base contracts upgraded

`assistant-mcp-base` now owns reusable MCP contracts:

- `McpTool`
- `McpToolDescriptor`
- `McpToolStatus`
- `McpToolResult`
- `McpToolContext`
- `McpToolExecutor`
- `McpToolRegistry`
- `FailClosedMcpToolExecutor`
- `ToolArgumentParser`
- `McpResultMapper`

The old `McpTool` shape remains source-compatible for existing simple tools: `call(argumentsJson: String)` still works. New Phase4 tools can use `call(argumentsJson, context)`.

### 3. assistant-tools registry upgraded

`NoteToolRegistry` now implements `McpToolRegistry` and `McpToolExecutor`:

- `listDescriptors()`
- `findTool(name)`
- `execute(name, argumentsJson, context)`
- existing `listToolNames()` kept for compatibility

### 4. Runtime MCP models unified toward base

`assistant-runtime` now depends on `assistant-mcp-base` and `McpProtocolClient` uses base-level `McpToolDescriptor`, `McpToolResult`, `McpToolStatus`, and `McpResultMapper`.

`McpProtocolResponse` remains inside runtime because it is a Xiaozhi/MCP JSON-RPC transport wrapper, not a reusable tool execution result.

### 5. Phase3 block behavior preserved

`McpProtocolClient` still returns Phase3-safe tools only and still blocks `notes.*` / `tags.*` while Phase4 executor wiring is not yet enabled.

## NoteCommandService support audit

Already executable in `NoteCommandService.execute`:

- `notes.create`
- `notes.search`
- `notes.list_recent`
- `notes.append`
- `notes.update_title`
- `notes.replace_content`
- `notes.toggle_done`
- `notes.pin`
- `notes.archive`
- `notes.delete`
- `notes.restore_revision`
- `tags.bind`
- `tags.delete`

Present in `ToolName` but not executable or falls through unsupported today:

- `notes.restore`
- `tags.search`
- `tags.create`
- `tags.rename`
- `ui.open_note`

Missing from `ToolName` / command path and should be added before exposing success-capable tools:

- `notes.get`
- `notes.list_by_tag`
- `notes.list_archived`
- `notes.list_deleted`
- `notes.list_todos`
- `notes.list_done`
- `notes.summarize`
- `notes.clear_done`
- `tags.list`
- `ui.show_search`
- `ui.show_note_list`
- `ui.show_tag`
- `ui.show_archive`
- `ui.show_trash`
- `ui.show_confirmation`
- `assistant.confirm`
- `assistant.reject`
- `assistant.list_pending_confirmations`

Phase4 Gate A should start with the already executable subset and return `not_implemented` for the rest until command/UI boundaries are added.

## Required manual step after applying overlay

Run one cleanup helper from repository root:

```bash
bash scripts/phase4-00-cleanup.sh
```

or on Windows PowerShell:

```powershell
.\scripts\phase4-00-cleanup.ps1
```

Then build and test.

## Safety boundary

This overlay does not add any direct DB, DAO, Room, or repository implementation dependency to `assistant-runtime`. Phase4 tools still need to be wired in later through `assistant-mcp-base` and `assistant-tools`.
