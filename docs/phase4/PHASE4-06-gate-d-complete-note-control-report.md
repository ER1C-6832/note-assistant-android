# Phase4-06 Gate D Complete Note Control Report

Status: overlay delivered, local Android build not run in generation environment.

## Scope

This overlay completes the Phase4 Gate D tool surface while keeping the Phase4 boundary:

- `assistant-runtime` continues to parse MCP and delegate to `assistant-mcp-base` executor interfaces.
- Real note/tag mutations live in `assistant-tools` and route through `NoteCommandService` where mutation logging / confirmation / revision support is required.
- UI tools emit `UiCommandBus` commands and do not mutate notes.

## Added tools

### Notes

- `notes.archive`
- `notes.restore`
- `notes.clear_done`
- `notes.list_archived`
- `notes.list_deleted`
- `notes.list_todos`
- `notes.list_done`

### Tags

- `tags.create`
- `tags.rename`
- `tags.list`
- `tags.bind` now supports `add`, `remove`, and `replace`.

### UI

- `ui.show_search`
- `ui.show_note_list`
- `ui.show_tag`
- `ui.show_archive`
- `ui.show_trash`

### Confirmation

- `assistant.list_pending_confirmations`

## Domain / data support

- Adds `ToolName.NotesClearDone`.
- Adds `NoteCommandService` handlers for:
  - `notes.restore`
  - `notes.clear_done`
  - `tags.create`
  - `tags.rename`
- Adds pending-confirmation listing through `CommandTraceRepository` and Room DAO.

## Manual acceptance

Gate D should be accepted only after fake runtime and real Xiaozhi runtime both pass the listed tools through the common MCP executor path.
