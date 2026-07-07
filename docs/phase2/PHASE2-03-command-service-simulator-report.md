# Phase2-03 Command Service and Local Simulator Report

## Scope

This overlay starts the executable Phase 2 command path without integrating real voice, WebSocket, microphone, wake word, or Xiaozhi runtime.

Implemented:

- `NoteCommandService` command boundary.
- Local tool simulator entry in Settings.
- Recent command log viewer in Settings.
- First batch command execution:
  - `notes.create`
  - `notes.search`
  - `notes.list_recent`
  - `notes.append`
  - `notes.update_title`
  - `notes.toggle_done`
  - `notes.pin`
  - `notes.archive`
- Command logs for every simulator execution.
- Revision snapshots before command-path mutation for append, title update, done toggle, archive, and batch pin.
- High-risk escalation for medium batch operations affecting more than five notes returns `requires_confirmation` and persists a pending confirmation.

Not implemented yet:

- Confirm/reject execution flow for pending confirmations.
- High-risk destructive commands such as `notes.delete`, `notes.replace_content`, `tags.delete`, `tags.bind replace`.
- Full transaction-backed mutation coordination. Trace records use `CommandTraceRepository`, and destructive/high-risk transaction handling is reserved for Phase2-04.
- Voice/MCP runtime integration.

## Acceptance notes

The simulator is in Settings. It is intentionally a developer/debug panel and does not replace the normal note editor.

Recommended validation:

1. Build and install the app.
2. Open Settings.
3. Use the Phase2 local tool simulator samples.
4. Execute `notes.create` and confirm a note appears on the home list.
5. Execute `notes.search` and confirm the same query can find the created note.
6. Execute `notes.append` with the created note id and confirm the note content changes.
7. Execute `notes.update_title` with the created note id and confirm the title changes.
8. Execute `notes.archive` and confirm the note moves to 已归档.
9. Return to Settings and confirm command logs are visible.

## Architecture notes

- `NoteCommandService` is in `notes-domain`.
- It calls existing Phase 1 UseCases and `CommandTraceRepository`.
- It does not call DAO directly.
- The Settings simulator calls `NoteCommandService`, not Repository or DAO.
- This keeps the future MCP/voice path aligned with the Phase 2 command boundary.
