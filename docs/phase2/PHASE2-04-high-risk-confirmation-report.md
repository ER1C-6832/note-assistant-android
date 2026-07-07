# Phase2-04 High Risk Confirmation Report

## Goal

Implement the first usable high-risk command confirmation loop for the local tool simulator while keeping Phase2 independent from real voice, WebSocket, microphone, and wake word behavior.

## Scope

Implemented in this overlay:

- `notes.delete`
- `notes.replace_content`
- `tags.bind`
- `tags.delete`
- pending confirmation confirm/reject flow in `NoteCommandService`
- simulator UI buttons for confirm/reject
- command log status updates for pending, confirmed, rejected, expired, blocked, success, and partial success paths
- revision snapshots before destructive or overwriting confirmed mutations

Not implemented in this overlay:

- real voice assistant runtime
- MCP network bridge
- full revision restore UI
- full expired-pending background cleanup UI
- polished user-facing confirmation bottom sheet outside the debug simulator

## Architecture

The simulator calls `NoteCommandService` only. `NoteCommandService` calls existing Phase1 UseCases and the Phase2 `CommandTraceRepository`.

No UI code calls DAO or Room directly.

## Command behavior

### notes.delete

Unconfirmed calls return `requires_confirmation` and persist a pending confirmation. Notes are not deleted until the simulator confirms the returned confirmation id.

On confirmation:

1. pending confirmation is marked confirmed,
2. revisions are created for target notes,
3. existing soft-delete UseCase is called,
4. command log is updated to success or partial success.

### notes.replace_content

Unconfirmed calls return `requires_confirmation` with old/new content preview and expected `updated_at`.

On confirmation, the note is reloaded. If it changed after preview, the command is blocked with `conflict` and no content is overwritten.

### tags.bind

- `mode=add` and `mode=remove` run as medium-risk direct commands.
- `mode=replace` requires confirmation.
- Replace writes note revisions before tag replacement.

### tags.delete

Unconfirmed calls return `requires_confirmation` with affected note preview and linked note count.

On confirmation:

1. linked notes are revalidated,
2. revisions are written for affected notes,
3. existing delete-tag UseCase is called,
4. command log records affected note ids and tag id.

## Simulator validation

Use Settings -> Phase2 Local Tool Simulator.

Recommended checks:

1. Create a note with `notes.create`.
2. Use its id in `notes.replace_content`.
3. Confirm the pending operation.
4. Check the note content changed and recent logs show confirmed success.
5. Run `notes.delete` against the note.
6. Reject once and verify the note remains.
7. Run delete again and confirm.
8. Verify the note moves to Recently Deleted.
9. Run `tags.bind` with `mode=add`, then `mode=replace` and confirm.
10. Run `tags.delete` with a real tag id and confirm.

## Notes

This is still a debug-oriented local simulator flow. The more polished product confirmation sheet can be added later, but the command path and persistence semantics are now testable from the app.
