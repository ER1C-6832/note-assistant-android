# Phase4-10: Auto Confirmation Experience

## Goal

Any MCP tool result with `status=requires_confirmation` should make the app show a confirmation dialog automatically. The server no longer needs to call `ui.show_confirmation` to make the dialog appear.

## Changes

- `ToolCallEventStore` now exposes `confirmationRequests` in addition to the existing tool call state/events.
- `McpProtocolClient` already writes every `tools/call` result into `ToolCallEventStore`; when the result is `requires_confirmation`, the store emits a `ToolCallConfirmationRequest`.
- `AppNavigation` collects `confirmationRequests` and shows `UiCommand.ShowConfirmation` automatically.
- Explicit `ui.show_confirmation` remains supported but is deduplicated by `confirmation_id`.
- Dialog confirm/reject buttons now call the MCP tools `assistant.confirm` / `assistant.reject` through `McpProtocolClient.handleToolCall`, instead of bypassing the MCP tool chain.
- `assistant.confirm {}` and `assistant.reject {}` remain the voice path for “确认 / 取消”. The existing tool behavior allows omitting `confirmation_id` when exactly one pending confirmation exists, and fails closed when multiple pending confirmations exist.

## Acceptance

1. Say: `小智，删除刚才那条便签`.
2. `notes.delete` returns `requires_confirmation`.
3. The app shows a confirmation dialog automatically.
4. Tap `确认执行`.
5. The app executes `assistant.confirm` through the MCP executor path and the note is deleted.
6. Tap `拒绝` on a fresh pending confirmation.
7. The app executes `assistant.reject` through the MCP executor path and the note is not deleted.
8. Say: `确认`.
9. If there is exactly one pending confirmation, the tool confirms it. If there are multiple, it asks for clarification instead of executing randomly.

## Deferred

`ui.show_search`, `ui.show_tag`, `ui.show_archive`, and `ui.show_trash` still navigate to the notes root. Real filter/search state handling belongs to Phase4-11.
