# Phase4-04 Buildfix 01 - UI samples

## Problem

Phase4-04 registered the Gate B tools, but the settings screen Phase4 MCP sample chips still showed only the old Gate A set.

## Fix

The patch script updates `phase4McpSamples` in `SettingsScreen.kt` to include:

- `notes.create`
- `notes.search`
- `notes.list_recent`
- `notes.get`
- `notes.append`
- `notes.update_title`
- `notes.toggle_done`
- `notes.pin`
- `ui.open_note`
- `tags.search`
- `tags.bind` with `mode=add`
- `notes.delete`

This only changes the debug UI sample list. It does not change runtime boundaries or tool execution behavior.
