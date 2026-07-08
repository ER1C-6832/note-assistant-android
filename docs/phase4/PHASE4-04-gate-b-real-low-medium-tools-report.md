# Phase4-04 Gate B: Real Low/Medium Voice Tools

## Scope

This overlay expands the Phase4 MCP executor tool set from Gate A local tools to Gate B low/medium tools that can be exercised from both Fake runtime and real Xiaozhi runtime through the same runtime path:

```text
Xiaozhi MCP tools/call
-> assistant-runtime McpProtocolClient
-> assistant-mcp-base McpToolExecutor
-> assistant-tools NoteToolRegistry
-> NoteCommandService / UiCommandBus / read-only UseCases
```

## Added tools

- `notes.update_title`
- `notes.toggle_done`
- `notes.pin`
- `ui.open_note`
- `tags.search`
- `tags.bind` in add-only mode

Existing Gate A tools remain registered:

- `notes.create`
- `notes.search`
- `notes.list_recent`
- `notes.get`
- `notes.append`
- `notes.delete`

## Safety boundaries

- `assistant-runtime` still only parses MCP and delegates to `assistant-mcp-base` executor interfaces.
- `assistant-runtime` does not import `NoteCommandService`, `notes-data`, Room DAO, or repository implementations.
- Note/tag mutations go through `NoteCommandService` in `assistant-tools`.
- `tags.bind` is restricted to `mode=add` in Gate B. `replace/remove` stay blocked until high-risk confirmation gates.
- `ui.open_note` emits `UiCommand.OpenNote` through `assistant-bridge`; app navigation observes the bus and opens the detail route.

## Manual acceptance

1. Build and install the app.
2. In settings, connect Fake runtime.
3. Use the Phase4 MCP debug panel to run `tools/list` and confirm all Gate A/B descriptors are returned.
4. Run `notes.create`, capture the returned `note_id`.
5. Run `notes.update_title`, `notes.append`, `notes.pin`, `tags.bind` add, `tags.search`, `notes.get`, and `ui.open_note` with that `note_id`.
6. Run real Xiaozhi runtime and ask the server to call the same tools.
7. Confirm command log entries are written for mutations and the note list/detail UI updates without manual reload.

## Not included

- Voice confirmation tools (`assistant.confirm`, `assistant.reject`) remain Phase4-05.
- High-risk tag replace/delete and destructive confirmation flows remain Phase4-05/06.
