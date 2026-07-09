# Phase4-12 buildfix-02: resolver target safety

## Problem

Voice phrases such as `把手柄便签标记完成` were being treated as a search string or were reduced too weakly. The resolver could match a stale or unrelated note, then `notes.toggle_done` mutated that wrong target.

## Changes

- Strengthened visible-reference extraction.
- `把手柄便签标记完成` now normalizes to `手柄`.
- Mutation/UI/list action words are not used as full-text fallback for resolver queries.
- `notes.toggle_done` now accepts `note_ref / note_title / title / query / exact_title` and resolves internally before mutation.
- `notes.convert_type` now accepts the same visible reference fields.
- If both `note_id` and visible reference are supplied but they point to different notes, the tool fails closed with `note_reference_mismatch` and does not mutate anything.
- Rich note results now include `safe_toggle_done_arguments` and `safe_convert_type_arguments` to nudge the server toward user-visible references.

## Acceptance

1. Say: `把手柄便签标记完成`.
2. Expected: resolver normalizes to `手柄` and picks the visible `手柄` note.
3. If that note is normal, `notes.toggle_done` converts it to todo and marks it done.
4. If a stale `note_id` conflicts with `note_ref=手柄`, the command fails closed and no note is modified.
