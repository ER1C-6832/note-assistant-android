# Phase4-06 buildfix-01: AppNavigation UiCommand Exhaustiveness

## Problem

After Phase4-06 added new UI commands, `AppNavigation.kt` still handled only:

- `UiCommand.OpenNote`
- `UiCommand.ShowMessage`
- `UiCommand.ShowConfirmation`

Kotlin sealed `when` became non-exhaustive because these commands were added:

- `UiCommand.ShowSearch`
- `UiCommand.ShowNoteList`
- `UiCommand.ShowTag`
- `UiCommand.ShowArchive`
- `UiCommand.ShowTrash`

## Fix

`AppNavigation.kt` now handles all `UiCommand` branches explicitly.

The current app only has routes for notes list, note detail, editor, color picker, settings, and splash. Therefore Phase4-06 UI presentation commands route to the notes root screen for now:

- `ui.show_search` -> notes root
- `ui.show_note_list` -> notes root
- `ui.show_tag` -> notes root
- `ui.show_archive` -> notes root
- `ui.show_trash` -> notes root

This keeps the app buildable and preserves the Phase4 boundary: UI commands navigate/present; they do not mutate notes.

## Follow-up

A later UI enhancement can add first-class filtered routes for search results, tag pages, archive, and trash. That is not required for this compile fix.
