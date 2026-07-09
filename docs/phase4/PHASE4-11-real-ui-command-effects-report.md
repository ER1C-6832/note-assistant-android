# Phase4-11 Real UI Command Effects

## Goal

Make UI MCP tools change the real notes list state instead of only navigating back to the root route.

## Implemented

- Added `NoteListExternalCommand` as a small app-to-notes-ui command model.
- `AppNavigation` now maps assistant `UiCommand` events to concrete list commands.
- `NoteListRoute` accepts `externalCommand` and forwards it to `NoteListScreen`.
- `NoteListScreen` applies external commands to its list filter and search state:
  - `ui.show_search` enters active-note search mode and fills the query.
  - `ui.show_tag` selects the matching tag filter by `tag_id` or `tag_name`.
  - `ui.show_archive` switches to the archived list.
  - `ui.show_trash` switches to the deleted list.
  - `ui.show_note_list` returns to all active notes.
  - `ui.show_pinned` switches to pinned notes.
- `ui.open_note` remains routed by `AppNavigation` directly to the detail page.

## Tool additions

- Added `UiCommand.ShowPinned`.
- Added `ui.show_pinned` MCP tool.
- Registered `ui.show_pinned` in `AssistantToolsModule`.

## Notes

The existing Settings debug panel remains available. The global assistant entry continues to sit above all non-splash pages.

## Manual acceptance

1. Trigger `ui.show_search` with `{ "query": "客户" }`.
   - Expected: notes root shows the search query and filtered results.
2. Trigger `ui.show_tag` with `{ "tag_name": "客户" }`.
   - Expected: notes root selects the matching tag filter.
3. Trigger `ui.show_archive`.
   - Expected: notes root switches to archived notes.
4. Trigger `ui.show_trash`.
   - Expected: notes root switches to recently deleted notes.
5. Trigger `ui.show_note_list`.
   - Expected: notes root switches back to active notes.
6. Trigger `ui.show_pinned`.
   - Expected: notes root switches to pinned notes.
7. Trigger `ui.open_note` with a valid `note_id`.
   - Expected: detail page opens for that note.
