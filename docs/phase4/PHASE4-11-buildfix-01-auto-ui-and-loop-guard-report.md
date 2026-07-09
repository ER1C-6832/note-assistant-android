# Phase4-11 buildfix-01: Auto UI effects and loop guard

## Why

After Phase4-11, UI commands such as `ui.show_search`, `ui.show_archive`, and `ui.show_trash` worked when called explicitly. In natural voice use, the model often calls read tools first, such as `notes.search` or `notes.list_archived`, and then asks the user whether to open or display results. This made the UI feel passive and caused extra follow-up turns.

The read-tool surface also still allowed tool loops for malformed broad list requests such as `全部用便签有哪些`, because the query was treated as a literal search term instead of a broad list intent.

## Changes

- `notes.search` now emits a UI command automatically:
  - normal query -> `UiCommand.ShowSearch(query)`
  - broad list intent -> `UiCommand.ShowNoteList`
  - archived scope -> `UiCommand.ShowArchive`
  - deleted scope -> `UiCommand.ShowTrash`
- `notes.resolve` now emits a UI command for resolved candidates.
- `notes.list_archived` now emits `UiCommand.ShowArchive`.
- `notes.list_deleted` now emits `UiCommand.ShowTrash`.
- `notes.list_pinned` now emits `UiCommand.ShowPinned`.
- `notes.list_by_tag` now emits `UiCommand.ShowTag`.
- Broad list intent handling added for phrases such as:
  - `全部便签有哪些`
  - `全部用便签有哪些`
  - `所有笔记有什么`
- Search fallback now uses cleaned terms before returning no match.
- Empty list/search results include a stop-loop hint instructing the assistant not to continue cycling through list/search tools.

## Files

- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NoteReferenceText.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NoteReferenceResolver.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesSearchTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesResolveTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/AbstractNoteListTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesListArchivedTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesListDeletedTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesListPinnedTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesListByTagTool.kt`

## Manual acceptance

1. Say: `搜索王总`.
   - Expected: `notes.search` returns results and the home list search box is updated automatically.
2. Say: `屏幕相关便签`.
   - Expected: search result UI updates without an extra `ui.show_search` call.
3. Say: `归档有哪些`.
   - Expected: `notes.list_archived` returns data and the archived list is displayed.
4. Say: `最近删除有哪些`.
   - Expected: `notes.list_deleted` returns data and trash list is displayed.
5. Say: `全部用便签有哪些`.
   - Expected: recognized as a broad list intent, active note list is returned/shown, and no tool loop occurs.
