# Phase4-12 buildfix-01: natural toggle done and note type conversion

## Scope

This overlay fixes the Phase4-12 real-utterance acceptance issue where a user says "mark it done" for a normal note.

## Changes

- `notes.toggle_done` now supports natural semantics:
  - If the target note is already a todo note, it toggles or sets `done` as before.
  - If the target note is a normal note and `done=true`, it first converts the note to todo and then marks it done.
  - If `auto_convert_to_todo=false`, normal notes still fail closed.
- Added `notes.convert_type` for explicit conversion between normal and todo notes.
- Added `ToolName.NotesConvertType` and registered it in risk policy as Medium.
- Registered `NotesConvertTypeTool` in Hilt multibinding.
- Conversion and natural toggle are handled inside `NoteCommandService`, so command logs and note revisions remain traceable.

## Voice acceptance

These utterances should now work:

- "把刚才那条标记完成"
- "把这条便签变成待办"
- "把这条便签变成待办并标记完成"

If the note is normal, the expected result is:

```json
{
  "converted_to_todo": true,
  "done": true
}
```

## Build command

```bat
gradlew.bat --stop
gradlew.bat clean :notes-domain:assembleDebug :assistant-tools:assembleDebug :app:assembleDebug --no-build-cache
```
