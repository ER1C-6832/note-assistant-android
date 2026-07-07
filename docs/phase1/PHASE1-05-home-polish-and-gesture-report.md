# Phase1-05 Home Polish and Gesture Report

## Scope

This patch continues Phase1-05 without changing the existing note data model or UseCase boundaries.

## Changes

- Moved the multi-select toolbar from the list content area to a floating bottom action panel.
- Added a separate selection mode so `全不选` clears selected items without exiting multi-select mode.
- Kept `退出` as the only explicit way to leave multi-select mode from the floating panel.
- Compacted the home header by removing the subtitle, reducing top spacing, and reducing the search box height.
- Moved the create button slightly lower while keeping it above the screen edge.
- Added horizontal swipe on the note list area:
  - Swipe left cycles through `全部 -> 置顶 -> 待办 -> 已完成`.
  - Swipe right moves backward.
  - Swipe right while on `全部` opens the tag drawer.
- Reordered top filters to `全部 / 置顶 / 待办 / 已完成`.
- Added the ability to pin one created tag to the home filter row.
- Removed the tag drawer title and subtitle.
- Reworked tag creation into a compact inline input + small create button.
- Kept only the created-tag list scrollable so system filters and tag creation stay fixed.

## Notes

- The pinned home tag is currently UI state remembered by Compose for the running app instance. It does not alter tag or note persistence.
- Batch actions still go through existing `NoteUseCases`; UI does not call DAO or Room directly.
- No root README changes were made.
