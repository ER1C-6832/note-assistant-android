# Phase4-09 buildfix-04: search/get rich grounding

## Problem

The assistant was still failing to find a visible note titled `1` even after list/search result enrichment.

The cause was not only `notes.delete`:

- `notes.search` and `notes.list_recent` still delegated to `NoteCommandService`, so they did not use the new rich note result serializer.
- `notes.search` defaulted to active notes only unless the model explicitly passed `include_archived` or `include_deleted`.
- `notes.get` still only accepted internal `note_id`, so it could not read a note by visible title/reference.
- Recent-list results are limited by `limit`; if a title is not in the first N items, the model must use exact title search instead of guessing an internal id.

## Fix

This overlay replaces these tools with direct read-only assistant-tools implementations:

- `notes.search`
- `notes.list_recent`
- `notes.get`
- shared list serialization used by Gate D list tools
- `notes.list_by_tag`
- `notes.list_pinned`

## Behavior

`notes.search` now defaults to `scope=all`, searching active, archived, and deleted notes unless explicitly scoped.

`notes.search` supports:

- `exact_title`
- `note_ref`
- `note_title`
- `title`
- `query`
- `scope=all|active|archived|deleted`
- `include_archived`
- `include_deleted`

Every returned note includes:

- `note_id`
- `note_ref`
- `user_visible_title`
- `title`
- `content`
- `snippet`
- `deleted`
- `archived`
- `pinned`
- `tags`
- `assistant_reference.safe_delete_arguments`

The result also includes `assistant_note_reference_rule` so the model is told that spoken numbers should not be invented as internal `note_id` values.

## Expected validation

1. Create or keep a note with title `1`.
2. Run `notes.search` with:

```json
{"exact_title":"1"}
```

Expected: it returns the note even if it is archived or recently deleted, with `deleted` / `archived` state visible.

3. Run `notes.get` with:

```json
{"note_ref":"1","scope":"all"}
```

Expected: it returns that note if the title is unique.

4. Run `notes.list_recent` with a low limit such as:

```json
{"limit":5}
```

Expected: it may not include title `1` if the note is older than the first five, but it returns `result_is_limited=true` and tells the assistant to call `notes.search` with `exact_title` instead of guessing an id.
