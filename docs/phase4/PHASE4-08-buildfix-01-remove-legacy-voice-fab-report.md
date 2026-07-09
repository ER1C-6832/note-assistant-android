# Phase4-08 buildfix-01 - remove legacy voice FAB

## Problem

After adding the global AssistantEntryOverlay, the notes home screen still displayed an older placeholder voice floating action button. This created two voice assistant entry points on the main note list.

## Fix

This overlay fully replaces `NoteListScreen.kt` and removes the legacy note-list-level blue `FloatingActionButton` placeholder.

## Kept

- Bottom-center `+ 新建` note button.
- Global AssistantEntryOverlay from AppNavigation/AppShell.
- Settings debug panel.
- Note list filtering, tag drawer, multi-select actions.

## Removed

- `onVoiceClick` callback plumbing inside `NoteListScreen`.
- Legacy bottom-end blue placeholder voice button.
- Obsolete imports for `FloatingActionButton` and `Modifier.shadow`.

## Verification

Expected result:

- Notes home screen has one assistant entry only: the global aurora assistant button.
- No extra blue placeholder button appears next to `+ 新建`.
