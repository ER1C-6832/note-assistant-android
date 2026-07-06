# Phase 1 Notes MVP Specification

> Status: Draft specification for Phase 1.
> Scope: Manual note-taking MVP only. Voice assistant, MCP tools, wake word, revisions, and command logs are out of Phase 1 unless explicitly marked as future compatibility.

## 1. Purpose

Phase 1 turns the app into a usable local-first notes product without relying on voice features.

The app must let a user create, view, edit, organize, search, complete, pin, soft-delete, and restore notes. The implementation must preserve the architectural rule from `docs/DEVELOPMENT_PLAN.md`: UI actions do not access DAO or Room directly. All user-visible note mutations go through domain UseCases.

## 2. Product Contract

From the user's point of view, Phase 1 must satisfy these promises:

- The app opens into a notes-first experience, not a demo screen.
- A user can create a note quickly, leave the editor, and see the note in the list without manual refresh.
- A user can edit a note and trust that title, content, type, color, tags, pinned state, done state, and deletion state are not accidentally overwritten by unrelated UI actions.
- A user can distinguish normal notes from todo notes.
- A user can mark todo notes done and later undo that completion.
- A user can find notes by visible text or tag.
- A user can soft-delete notes and restore them before permanent cleanup exists.
- The app must avoid destructive surprises. Deletion is soft deletion in Phase 1.

From the developer's point of view, Phase 1 must satisfy these constraints:

- `notes-ui` may depend on domain models and UseCases, but must not depend on Room entities or DAOs.
- `notes-domain` must not depend on Compose, Android UI, Room, or data-layer entities.
- `notes-data` maps between Room entities and domain models.
- Every write operation is represented by a UseCase, even when the first implementation delegates directly to Repository.
- Search, filters, tags, and status flags must be modeled as domain concepts rather than UI-only strings.

## 3. Phase 1 Boundaries

### 3.1 Included

- Splash screen and app shell.
- Notes list.
- Empty state.
- Create normal note.
- Create todo note.
- Open existing note.
- Edit title.
- Edit content.
- Edit type: normal or todo.
- Edit color.
- Add and remove lightweight tags in Phase1-01, then formal tag entities in Phase1-03.
- Pin and unpin.
- Mark todo done and not done.
- Soft delete.
- Multi-select delete if multi-select UI is implemented in Phase 1.
- Restore from recently deleted.
- Search title, content, and tags.
- Filter by all, todo, done, pinned, archived, deleted, and tag when the related feature is present.

### 3.2 Excluded

- Voice assistant behavior.
- MCP tools behavior.
- Wake word and background operation.
- Command log.
- Revision history UI.
- Markdown rendering.
- Cloud sync.
- Cross-device conflict resolution.
- Permanent delete, except optional developer-only cleanup.
- Rich text or block editing.

## 4. Terms

- `Note`: A user-created record with title, content, type, status flags, timestamps, color, and tags.
- `Normal note`: A note whose `type` is `normal`.
- `Todo note`: A note whose `type` is `todo`; it can be done or not done.
- `Active note`: A note where `deleted=false` and `archived=false`.
- `Deleted note`: A soft-deleted note where `deleted=true`.
- `Archived note`: A hidden note where `archived=true`.
- `Tag`: A user-visible label attached to one or more notes.
- `Lightweight tag text`: Temporary Phase1-01 storage in `notes.tag_text`.
- `Formal tag`: A tag stored in `tags` and connected through `note_tag_cross_ref`.
- `Manual source`: A write caused by direct UI action, represented as `lastEditedSource=manual`.

## 5. Global Rules

### 5.1 Data Integrity

- A note must always have an `id`, `title`, `content`, `type`, `createdAt`, `updatedAt`, `pinned`, `archived`, `deleted`, and `lastEditedSource`.
- `createdAt` is set only once when the note is created.
- `updatedAt` changes whenever user-visible note content or organization state changes.
- `doneAt` must be non-null only when `type=todo` and `isDone=true`.
- If a todo note is changed back to normal, `isDone` must become `false` and `doneAt` must become `null`.
- Phase 1 must not physically delete notes during normal user flows.
- Deleted notes must not appear in the default list or default search results.
- Archived notes must not appear in the default list or default search results unless the selected filter includes archived notes.

### 5.2 Input Normalization

- Leading and trailing whitespace in titles and tags must be trimmed before saving.
- Content may preserve internal line breaks and user-entered spacing.
- Empty title is allowed only if the UI provides a stable fallback display such as `未命名便签`.
- Empty content is allowed.
- A note with both empty title and empty content may be discarded on explicit back navigation only if it has never been saved.
- Duplicate tags on the same note must collapse into one visible tag.
- Tag separators in Phase1-01 lightweight input are: Chinese comma `，`, normal comma `,`, dunhao `、`, whitespace, and `#`.

### 5.3 Ordering

Default active list ordering must be:

1. Pinned notes first.
2. Within pinned and unpinned groups, newest `updatedAt` first.
3. If timestamps are equal, larger `id` first.

Done todo notes may be visually de-emphasized, but must not disappear from `全部` unless the user selected a filter that hides done notes.

### 5.4 Feedback

- Successful create returns to the list and the created note is visible without manual refresh.
- Successful edit returns to the list or updates the editor state without stale data.
- Successful delete removes the note from the current active list immediately.
- Failed operations must show a user-understandable message and must not silently drop user input.
- Loading states must not block the whole app longer than necessary for local database work.

### 5.5 Performance Targets

- Opening the list from cold app start should show either content or empty state within 1000ms after splash completes on a normal test device.
- Local create, update, pin, done toggle, and soft delete should complete within 200ms for a single note.
- Search should return visible results within 500ms for 5000 local notes.
- UI state refresh after a local database write should be visible within 500ms.

## 6. Data Contract

### 6.1 Note Fields

Required Phase 1 domain fields:

```text
id: Long
title: String
content: String
type: normal | todo
isDone: Boolean
doneAt: Long?
pinned: Boolean
archived: Boolean
deleted: Boolean
color: String?
createdAt: Long
updatedAt: Long
lastEditedSource: manual
tags: List<Tag>
```

Required Phase 1 data-layer compatibility fields:

```text
content_format: plain
archived_at: Long?
deleted_at: Long?
tag_text: String
reminder_at: Long?
sort_order: Long?
source_conversation_id: String?
```

### 6.2 Tag Fields

Formal Tag CRUD is a Phase1-03 target. When formal tags are enabled:

```text
id: Long
name: String
normalizedName: String
color: String?
createdAt: Long
updatedAt: Long
```

Rules:

- `normalizedName` must be unique.
- Deleting a tag removes note-tag relationships only. It must not delete notes.
- Tag display order should be stable: user-created order or alphabetical by normalized name. The chosen rule must be consistent.

## 7. UI Specification

### 7.1 Splash

- The splash screen displays the app identity `小泓便签`.
- It must not request microphone or notification permissions.
- It must navigate automatically to the notes list.
- If database initialization fails, the user must see a recoverable error message instead of a blank screen.

### 7.2 Notes List

The list screen must contain:

- Product identity.
- Settings entry.
- Search entry.
- Filter chips for `全部`, `待办`, `已完成`, and `置顶`.
- Tag entry or tag drawer once tags exist.
- Note cards.
- Create note action.
- Voice entry may be visible as a placeholder, but must not trigger Phase 1 note mutations.

Each note card must show:

- Title fallback if title is blank.
- Content preview if content is not blank.
- Todo checkbox when `type=todo`.
- Done visual state when `isDone=true`.
- Tag chips when tags exist.
- Pinned marker when `pinned=true`.
- Updated time or relative time if the UI includes timestamps.

### 7.3 Editor

The editor must allow:

- Title input.
- Content input.
- Normal/todo selection.
- Color selection.
- Tag input or tag picker.
- Save action.
- Back navigation.

Editor rules:

- Existing note data must load before showing editable fields as final values.
- While loading an existing note, the UI must not present a blank editor that can overwrite the note accidentally.
- Save must be disabled or no-op while a save is already in progress.
- If save fails, user-entered title/content/tags remain visible.

### 7.4 Deleted Notes View

If Phase 1 exposes `最近删除`:

- Deleted notes appear only in the deleted filter/view.
- Deleted notes can be restored.
- Deleted notes should visually indicate they are in the recycle area.
- Editing a deleted note is optional in Phase 1; if disabled, the UI must explain that the note needs to be restored first.

## 8. UseCase Behavior Cards

### UC-01 Create Normal Note

- Actor: User through editor UI.
- Precondition: App database is available.
- Input: title, content, color, tags.
- Normal flow:
  1. User opens the editor through create action.
  2. User selects normal note or leaves the default as normal.
  3. User enters title and/or content.
  4. User saves.
  5. System creates a note with `type=normal`, `isDone=false`, `doneAt=null`, `deleted=false`, `archived=false`, and `lastEditedSource=manual`.
  6. System sets `createdAt=now` and `updatedAt=now`.
  7. System returns to list and shows the new note according to default ordering.
- Negative paths:
  - If both title and content are empty, system may reject save with `请输入标题或正文` or discard only after explicit user confirmation/back behavior.
  - If database write fails, system shows `保存失败，请重试` and keeps editor content.
- Acceptance:
  - Given the user is on the list, When they create a normal note titled `测试`, Then the note appears in the list without manual refresh.
  - Given a note is created, Then `createdAt` and `updatedAt` are set and `createdAt <= updatedAt`.

### UC-02 Create Todo Note

- Actor: User through editor UI.
- Precondition: App database is available.
- Input: title, content, color, tags.
- Normal flow:
  1. User opens editor.
  2. User selects todo type.
  3. User saves.
  4. System creates a note with `type=todo`, `isDone=false`, `doneAt=null`.
  5. List card displays a checkbox.
- Negative paths:
  - Same as UC-01.
- Acceptance:
  - Given the user creates a todo note titled `买牛奶`, Then the list card shows an unchecked checkbox and the title `买牛奶`.

### UC-03 Open Existing Note

- Actor: User tapping a note card.
- Precondition: The note exists and is not physically deleted.
- Normal flow:
  1. User taps a note card.
  2. System loads the note by id through UseCase.
  3. Editor displays title, content, type, color, and tags.
- Negative paths:
  - If note id no longer exists, show `便签已不存在` and return to list.
  - If note is soft-deleted and the user opened it from active list due to stale UI, remove it from active list and show `便签已在最近删除中`.
- Acceptance:
  - Given a note has tags and color, When opened, Then editor fields match the saved note.

### UC-04 Update Title and Content

- Actor: User through editor UI.
- Precondition: Note exists and is not soft-deleted unless editing deleted notes is explicitly allowed.
- Normal flow:
  1. User edits title and/or content.
  2. User saves.
  3. System updates only the fields represented in the editor state.
  4. System updates `updatedAt=now` and `lastEditedSource=manual`.
  5. System preserves unrelated fields such as `pinned`, `deleted`, `archived`, `isDone`, and tags unless explicitly changed.
- Negative paths:
  - If note does not exist, return a not-found result and show `便签已不存在`.
  - If save fails, editor content remains on screen.
- Concurrency:
  - If Phase 1 has no version column, last write wins is acceptable only for manual same-device UI. The implementation must not perform partial writes from stale list cards.
  - A future version field is recommended before enabling voice/background concurrent edits.
- Acceptance:
  - Given a pinned todo note with tags, When the user edits only content, Then pinned state, todo state, and tags remain unchanged.

### UC-05 Change Note Type

- Actor: User through editor UI.
- Precondition: Note exists.
- Normal flow:
  - Normal to todo: set `type=todo`, keep `isDone=false`, keep `doneAt=null`.
  - Todo to normal: set `type=normal`, set `isDone=false`, set `doneAt=null`.
  - Update `updatedAt`.
- Negative paths:
  - If the type value is unknown, reject the change and keep previous type.
- Acceptance:
  - Given a done todo note, When changed to normal, Then checkbox disappears and `doneAt=null`.

### UC-06 Toggle Todo Done

- Actor: User tapping checkbox.
- Precondition: Note exists, `type=todo`, `deleted=false`.
- Normal flow:
  - If `done=false`, set `isDone=true`, `doneAt=now`, `updatedAt=now`.
  - If `done=true`, set `isDone=false`, `doneAt=null`, `updatedAt=now`.
  - List updates immediately.
- Negative paths:
  - If note is normal, checkbox must not be shown. If the UseCase is called anyway, return validation error.
  - If note is deleted, reject with `便签已删除，无法完成`.
- Acceptance:
  - Given an active todo note, When the user taps its checkbox, Then it becomes done and shows done styling.
  - Given a done todo note, When the user taps again, Then done styling is removed.

### UC-07 Pin and Unpin

- Actor: User from card action, editor action, or multi-select action.
- Precondition: Note exists and is not deleted.
- Normal flow:
  - Set `pinned` to requested value.
  - Update `updatedAt`.
  - Re-sort list using global ordering.
- Negative paths:
  - If note is deleted, reject unless the deleted view explicitly supports pin changes.
- Acceptance:
  - Given an unpinned note exists below newer notes, When pinned, Then it moves into the pinned group.

### UC-08 Soft Delete

- Actor: User from card action, editor action, or multi-select action.
- Precondition: Note exists and `deleted=false`.
- Normal flow:
  1. User requests delete.
  2. If a confirmation UI exists, user confirms.
  3. System sets `deleted=true`, `deletedAt=now`, `updatedAt=now`.
  4. System removes the note from active list.
  5. System shows feedback such as `已删除，可在最近删除中恢复`.
- Negative paths:
  - If note does not exist, show `便签已不存在`.
  - If note is already deleted, show `该便签已在最近删除中`.
- Acceptance:
  - Given an active note in the list, When deleted, Then it disappears from `全部`.
  - Given the deleted filter is opened, Then the deleted note is visible there.

### UC-09 Restore Deleted Note

- Actor: User from recently deleted view.
- Precondition: Note exists and `deleted=true`.
- Normal flow:
  - Set `deleted=false`, `deletedAt=null`, `updatedAt=now`.
  - Restored note appears in active lists according to ordering.
- Negative paths:
  - If note is not deleted, show `便签已经在列表中`.
- Acceptance:
  - Given a deleted note, When restored, Then it appears in `全部` and no longer appears in `最近删除`.

### UC-10 Archive and Unarchive

- Actor: User from action menu or multi-select action.
- Precondition: Note exists and `deleted=false`.
- Normal flow:
  - Archive: set `archived=true`, `archivedAt=now`, `updatedAt=now`, remove from active list.
  - Unarchive: set `archived=false`, `archivedAt=null`, `updatedAt=now`.
- Negative paths:
  - If note is deleted, archive action is unavailable or rejected.
- Acceptance:
  - Given an active note, When archived, Then it disappears from default list and appears only when archived filter is selected.

### UC-11 Edit Tags

- Actor: User through editor tag input or tag picker.
- Precondition: Note exists for editing, or editor is creating a new note.
- Normal flow:
  - Parse user input into tag names.
  - Trim each tag.
  - Remove duplicates.
  - Save tags with the note.
  - Display saved tags on the note card.
- Phase1-01 compatibility:
  - Tags may be stored in `notes.tag_text`.
  - Type labels such as `普通` and `待办` must not be treated as tags unless the user explicitly entered them as tags.
- Phase1-03 formal behavior:
  - Missing tags are created through tag UseCase.
  - Relationships are stored in `note_tag_cross_ref`.
- Negative paths:
  - Empty tag input means no tags.
  - Invalid/overlong tag names are ignored or rejected with a visible message. The chosen rule must be consistent.
- Acceptance:
  - Given the user enters `客户、硬件 #报价`, When saved, Then the note displays tags `客户`, `硬件`, and `报价`.
  - Given the user changes a note from todo to normal, Then existing tags are preserved and not replaced by `普通`.

### UC-12 Create, Rename, and Delete Formal Tags

- Actor: User through tag drawer or tag management UI.
- Precondition: Formal tags are enabled.
- Normal flow:
  - Create: save unique normalized tag and make it selectable.
  - Rename: update display name and normalized name if no conflict exists.
  - Delete: remove tag relationships, then remove tag.
- Negative paths:
  - Duplicate normalized name: show `标签已存在`.
  - Delete tag with linked notes: require confirmation or clearly state that notes will not be deleted.
- Acceptance:
  - Given a tag is deleted, Then notes linked to it remain visible without that tag.

### UC-13 Search Notes

- Actor: User through search input.
- Precondition: Database is available.
- Normal flow:
  - User enters query.
  - System normalizes query by trimming whitespace.
  - System searches title, content, and tags.
  - System excludes deleted and archived notes unless explicitly included by filter.
  - Results are sorted by relevance, pinned status, and recency.
- Relevance minimum:
  1. Exact title match.
  2. Title contains query.
  3. Tag contains query.
  4. Content contains query.
  5. Recent updates break ties.
- Negative paths:
  - Empty query returns current filtered list or recent notes, depending on UI mode.
  - No results shows an empty result state, not a blank page.
- Acceptance:
  - Given one note title contains `报价` and another only content contains `报价`, When searching `报价`, Then the title match appears before the content match.
  - Given a deleted note contains `报价`, When searching normally, Then it does not appear.

### UC-14 Filter Notes

- Actor: User selecting chips, tag drawer, or system filters.
- Normal flow:
  - `全部`: active notes, including normal and todo, excluding deleted and archived.
  - `待办`: active notes where `type=todo` and `isDone=false`.
  - `已完成`: active notes where `type=todo` and `isDone=true`.
  - `置顶`: active notes where `pinned=true`.
  - `归档`: archived notes where `deleted=false`.
  - `最近删除`: deleted notes.
  - Tag filter: active notes linked to selected tag unless combined with another explicit status filter.
- Acceptance:
  - Given a done todo exists, When `已完成` is selected, Then it appears.
  - Given a normal note is pinned, When `置顶` is selected, Then it appears.

### UC-15 Multi-Select Actions

- Actor: User long-pressing cards.
- Precondition: Multi-select UI is enabled.
- Normal flow:
  - Long press enters selection mode.
  - Selected count is visible.
  - User can clear selection.
  - Supported batch actions operate only on selected notes.
- Required batch actions if included:
  - Soft delete selected notes.
  - Pin or unpin selected notes.
  - Archive selected notes.
  - Add or remove tag.
- Negative paths:
  - If selection contains notes no longer active, skip stale notes and report how many were affected.
- Acceptance:
  - Given three notes are selected, When delete is confirmed, Then all three disappear from active list and appear in recently deleted.

## 9. Error and Edge Case Matrix

| Case | Required behavior |
| --- | --- |
| Database read fails | Show recoverable error state with retry. |
| Database write fails | Keep user input and show save/delete failure message. |
| Note id not found | Show `便签已不存在` and return to a safe screen. |
| Saving while previous save is running | Ignore second save or disable save button. |
| Empty list | Show empty state and create action. |
| Empty search result | Show no-result state and keep query visible. |
| Very long title | UI must wrap or ellipsize without breaking layout. |
| Very long content | Editor remains scrollable; list card shows preview only. |
| Duplicate tags | Save one visible tag per normalized name. |
| Deleted note in active Flow due to stale state | Remove from active list on next emission. |
| App process killed after save | Saved note must still exist after restart. |

## 10. Concurrency Rules

Phase 1 is local-first and manual-only, so the main concurrency risk is UI stale state, double taps, and multiple screens.

- UseCases must be safe to call repeatedly.
- Save buttons should be disabled while saving.
- Delete, restore, pin, archive, and done toggle must be idempotent for the target final state.
- A stale editor must not clear fields it did not own. Repository update APIs should either update complete note snapshots deliberately or expose focused updates for individual operations.
- Before Phase 3/4 voice operations, the project should introduce either a version column or a conflict policy. Phase 1 may document this as a future requirement, but must avoid patterns that make conflict handling impossible.

## 11. Test Acceptance Suite

Minimum tests for Phase 1:

- Repository or DAO test: create note persists all required fields.
- UseCase test: create normal note sets normal defaults.
- UseCase test: create todo note sets todo defaults.
- UseCase test: update content preserves pinned, deleted, archived, done state, and tags.
- UseCase test: normalizing tag input removes duplicates and separators.
- UseCase test: toggle done rejects normal notes.
- UseCase test: soft delete hides notes from active list.
- UseCase test: restore returns deleted note to active list.
- UseCase test: default ordering puts pinned notes first, then newest updated.
- Search test: title match outranks content match.
- Search test: deleted and archived notes are excluded by default.
- UI test: create note from list, save, and see it in list.
- UI test: open existing note, edit, save, and see updated card.
- UI test: todo checkbox toggles visible done state.
- UI test: empty list and empty search states render without overlap.

## 12. Phase Completion Definition

Phase 1 is complete when:

- A user can use the app as a standalone manual notes app without voice features.
- All included UseCases have matching UI paths or are explicitly marked as backend-ready only.
- Default list, editor, tag behavior, soft delete, restore, todo completion, pinning, and search pass the acceptance suite.
- No UI write path directly uses DAO.
- No source file in `notes-ui` imports Room entities or DAO classes.
- No source file in `notes-domain` imports Android UI, Compose, Room, or data-layer entities.
- Known limitations are documented in `docs/phase1/`.

## 13. Traceability

This spec refines the Phase 1 section of:

- `docs/DEVELOPMENT_PLAN.md`

Current Phase 1 reports:

- `docs/phase1/PHASE1-01-app-shell-and-notes-crud-report.md`
- `docs/phase1/PHASE1-01-ui-and-tag-fix-report.md`

When implementation changes behavior, update this spec first or update it in the same commit as the behavior change.
