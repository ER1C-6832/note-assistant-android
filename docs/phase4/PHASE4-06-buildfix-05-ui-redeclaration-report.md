# Phase4-06 buildfix-05: UI tool redeclaration fix

## Problem

`assistant-tools:compileDebugKotlin` failed because concrete UI MCP tool classes were declared twice:

- `UiListSurfaceTools.kt` contained `UiShowSearchTool`, `UiShowNoteListTool`, `UiShowTagTool`, `UiShowArchiveTool`, and `UiShowTrashTool`.
- Separate files with the same class names also existed.

The build also failed because the old shared base kept `uiCommandBus` private while concrete subclasses attempted to access it.

## Fix

- `UiListSurfaceTools.kt` now contains only the shared abstract `AbstractUiSurfaceTool`.
- Concrete `UiShow*Tool` classes remain in their own files only.
- `uiCommandBus` is `protected` inside the abstract base.
- No Hilt bindings were changed.

## Boundary

This buildfix does not change note mutation behavior. Runtime still delegates MCP calls through the shared executor path, and UI tools only emit `UiCommand` events.
