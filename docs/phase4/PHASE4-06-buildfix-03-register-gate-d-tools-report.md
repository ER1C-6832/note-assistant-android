# Phase4-06 buildfix-03: register Gate D tools

This overlay fixes the Gate D regression where UI samples existed but the corresponding tools were not bound into the Hilt `Set<McpTool>`, so `tools/call` returned unsupported/unregistered results.

Implemented/registered:

- `notes.archive`
- `notes.restore`
- `notes.clear_done`
- `notes.list_archived`
- `notes.list_deleted`
- `notes.list_todos`
- `notes.list_done`
- `tags.create`
- `tags.rename`
- `tags.list`
- `tags.bind` add/remove/replace
- `assistant.list_pending_confirmations`
- `ui.show_search`
- `ui.show_note_list`
- `ui.show_tag`
- `ui.show_archive`
- `ui.show_trash`

Safety notes:

- Existing command-backed tools still use `NoteCommandService`.
- Additional Gate D command gaps are handled by `Phase4ExtendedCommandService`, which uses Phase2 command trace storage, pending confirmations, and revision snapshots.
- `assistant-runtime` is not modified and still does not import note business implementation.
