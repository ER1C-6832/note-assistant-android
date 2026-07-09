# Phase4-06 Buildfix 02 - UI MCP Samples

This buildfix replaces the Settings screen with a full-file overlay and expands the Phase4 MCP simulator samples.

## Fixed

- Added Phase4 Gate D simulator buttons for:
  - notes.archive
  - notes.restore
  - notes.clear_done
  - notes.list_archived
  - notes.list_deleted
  - notes.list_todos
  - notes.list_done
  - tags.create
  - tags.rename
  - tags.list
  - tags.bind add/remove/replace
  - assistant.list_pending_confirmations
  - ui.show_search
  - ui.show_note_list
  - ui.show_tag
  - ui.show_archive
  - ui.show_trash
- Preserved Phase3 runtime controls, Phase4 runtime MCP call path, Phase2 simulator, revision debug and command log.

## Boundary

The Settings UI only sends simulated MCP tools/call through AssistantController. It does not execute notes tools directly for Phase4 validation.
