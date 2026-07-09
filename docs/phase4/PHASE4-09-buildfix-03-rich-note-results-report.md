# Phase4-09 buildfix-03: Rich note results for voice tool grounding

## Problem

The assistant often calls `notes.list_recent` before deciding which note to delete. If list/search results only expose an internal `note_id` with a short title/snippet, the model may treat a user-visible title like `1` as internal `note_id=1`.

## Fix

This overlay makes read tools return richer note payloads:

- `title`
- `note_ref`
- `user_visible_title`
- `content`
- `snippet`
- `tags` / `tag_names`
- `type`
- `done`
- `pinned`
- `archived`
- `deleted`
- timestamps
- an `assistant_note_reference_rule`
- per-note `assistant_reference.safe_delete_arguments`

Updated tools:

- `notes.search`
- `notes.list_recent`
- `notes.list_archived`
- `notes.list_deleted`
- `notes.list_todos`
- `notes.list_done`
- `notes.list_pinned`
- `notes.list_by_tag`
- `notes.get`

## Behavior

The assistant should identify notes using user-visible fields first. It may use `note_id` only when that id came from the current tool result, not by guessing from speech.

Example safe flow:

1. User: delete the note titled 1.
2. Assistant calls `notes.search` or `notes.list_recent`.
3. It sees `title=1`, `note_ref=1`, and content.
4. It calls `notes.delete` with `{"note_ref":"1"}` or with the exact returned `note_id` only if it is selecting from that result.

## Boundary

No runtime boundary change. This only improves assistant-tools read results and descriptors.
