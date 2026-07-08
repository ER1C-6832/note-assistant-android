# Phase4-03 Gate A Local Tools Report

## Status

Gate A local executor wiring is implemented for the first real Phase 4 tool set.

## Implemented

- `tools/list` can return Phase 4 descriptors once `assistant-tools` is present in the Hilt graph.
- `assistant-runtime` still delegates through `assistant-mcp-base.McpToolExecutor` and does not import `NoteCommandService`.
- `assistant-tools` contributes one `McpToolExecutor` via `NoteToolRegistry`.
- `assistant-tools` registers these tools:
  - `notes.search`
  - `notes.list_recent`
  - `notes.get`
  - `notes.create`
  - `notes.append`
  - `notes.delete`
- Command-backed tools use `NoteCommandService`:
  - `notes.search`
  - `notes.list_recent`
  - `notes.create`
  - `notes.append`
  - `notes.delete`
- `notes.delete` returns `requires_confirmation` for unconfirmed requests through the persisted Phase 2 pending confirmation path.
- `notes.get` is implemented as a low-risk read-only tool through `NoteUseCases.getNote`. It does not mutate notes.

## Safety boundary

- Note mutations do not call DAO, Room, or repository implementations directly.
- Note mutations go through `NoteCommandService`.
- Unknown tools still return `not_implemented`.
- Invalid JSON and missing required arguments return safe failed results.
- High-risk delete does not mutate before confirmation.

## Manual acceptance

1. Build and install the app.
2. Open Settings -> Phase3/Phase4 assistant runtime debug area.
3. Use Fake Runtime.
4. Trigger `tools/list` and verify Phase4 descriptors include `notes.create`, `notes.search`, `notes.list_recent`, `notes.get`, `notes.append`, and `notes.delete`.
5. Trigger `tools/call notes.create` and verify a note is created through the command path.
6. Trigger `tools/call notes.search` and verify real local results are returned.
7. Trigger `tools/call notes.list_recent` and verify recently created note is first.
8. Trigger `tools/call notes.get` with a concrete id and verify full fields return.
9. Trigger `tools/call notes.append` and verify revision/log behavior remains handled by Phase 2.
10. Trigger `tools/call notes.delete` and verify it returns `requires_confirmation` and does not delete before confirmation.

## Next

Phase4-04 should expand to real voice low/medium operations: update title, toggle done, pin, `ui.open_note`, `tags.search`, and `tags.bind.add`.
