# Phase4-05 Gate C High Risk Confirmation

Status: overlay prepared.

## Scope

Gate C adds the high-risk MCP confirmation path while preserving the Phase4 boundary:

- `assistant-runtime` parses MCP and delegates to `McpToolExecutor` only.
- High-risk note/tag tools live in `assistant-tools`.
- Mutations continue through Phase2 `NoteCommandService`, command logs, pending confirmations, and revision snapshots.
- Confirmation execution is done through persisted `PendingConfirmation`, not by accepting an arbitrary `confirmed=true` shortcut from the assistant.

## Added tools

- `notes.replace_content`
- `notes.restore_revision`
- `tags.delete`
- `assistant.confirm`
- `assistant.reject`
- `ui.show_confirmation`

## Updated tools

- `tags.bind` now supports `mode=add` and `mode=replace`.
- `mode=replace` must return `requires_confirmation` before mutation.
- Unsupported tag bind modes still fail closed.

## Manual acceptance

Use Fake Runtime first, then Real Xiaozhi Runtime:

1. Create a test note.
2. Call a high-risk tool such as `notes.replace_content` or `notes.delete`.
3. Verify the result is `requires_confirmation` and no note changed yet.
4. Copy the returned `confirmation_id`.
5. Call `assistant.confirm` with that id and verify the mutation occurs.
6. Repeat with `assistant.reject` and verify no mutation occurs.
7. Call `ui.show_confirmation` with a pending id and verify the app displays a confirmation dialog.

## Not included

- Generic natural-language reference resolution.
- Full confirmation list UI.
- Batch archive/restore/trash tools. Those remain for later Gate D work.
