# Phase4-09 Tool Call Feedback Overlay

## Scope

This overlay adds product-visible MCP tool call feedback for the global assistant entry.

## Runtime changes

- Adds `ToolCallUiState`, `ToolCallEvent`, and `ToolCallEventStore` under `assistant-runtime`.
- `McpProtocolClient` now marks a tool call as running before executing `tools/call` and marks it completed after the executor returns.
- The state records:
  - tool name
  - UI status
  - user-facing message
  - command log id
  - confirmation id
  - confirmation summary
  - error code

## UI changes

- `AssistantEntryOverlay` observes `ToolCallEventStore.state` through Hilt.
- Adds an `AssistantOperationBanner` above the global assistant button.
- Adds an inline summary inside the expanded assistant panel.

## Expected behavior

- `notes.create` shows `正在创建便签` while executing, then `已创建便签`.
- `notes.append` shows `正在追加内容`, then `已追加内容`.
- `notes.delete` shows `需要确认删除` when the tool returns `requires_confirmation`.
- Failed, blocked, and not implemented results show a short user-facing failure message without stack traces.

## Architecture

The runtime still delegates all MCP execution through the injected executor. The app UI only observes runtime-exposed tool call state and does not call note tools directly.
