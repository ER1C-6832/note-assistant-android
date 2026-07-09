# Phase4-09 buildfix-02: note reference and confirmation idempotency

## Scope

This overlay fixes two product-level issues found during real voice validation:

1. A confirmed high-risk operation could be followed by a stale `ui.show_confirmation` or duplicate `assistant.confirm` call, causing a visible failure after the note had already been deleted.
2. Voice deletion could treat a user-visible title like `1` as internal `note_id=1`, even though users do not see internal note IDs.

## Changes

### Confirmation chain

- `AppNavigation` now ignores duplicate `UiCommand.ShowConfirmation` events for the same confirmation id once an auto confirmation dialog has already been shown or handled.
- `assistant.confirm` is idempotent for an already confirmed `confirmation_id`. A duplicate confirm now returns success-like feedback instead of surfacing a stale failure.

### User-visible note reference

- `notes.delete` now supports user-visible reference fields:
  - `note_ref`
  - `note_title`
  - `title`
  - `query`
- In voice context, a numeric `note_id` without `force_note_id=true` is treated carefully. If there is an active or archived note whose title exactly equals that number, the tool resolves to that title match instead of blindly deleting the internal ID.
- Ambiguous title matches fail closed and ask the user to be more specific.
- Missing title matches fail closed and recommend searching first.
- Internal ID deletion still works with `force_note_id=true` or `id_is_internal=true`.

## Files

- `app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/assistant/AssistantConfirmTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesDeleteTool.kt`

## Validation

Manual validation should cover:

1. Say: delete the note titled `1`.
   - Expected: tool resolves title `1`, not internal `note_id=1`.
   - Expected: delete returns `requires_confirmation`.
   - Expected: confirmation dialog appears once.
   - Expected: pressing confirm deletes the intended note.
   - Expected: no follow-up stale confirmation failure appears.

2. If two notes match the same spoken title/keyword:
   - Expected: no deletion happens.
   - Expected: tool asks for a more specific title.

3. If a true internal ID path is needed:
   - Use `{ "note_id": 1, "force_note_id": true }`.
