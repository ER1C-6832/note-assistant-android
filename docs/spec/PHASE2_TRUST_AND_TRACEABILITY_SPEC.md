# Phase 2 Trust and Traceability Specification

> Status: Draft specification for Phase 2.
> Scope: Trusted operation layer, traceability, confirmation, undo, local tool simulation, and preparation for future voice/MCP entry points.
> Phase 2 is not a second notes MVP. Phase 1 already owns the manual notes product behavior.

## 1. Purpose

Phase 2 makes the existing notes product safe for non-UI automation.

Phase 1 proves that users can use the app as a real manual notes app. Phase 2 adds the layer that answers these questions before voice assistant and MCP integration become real:

- What operation was requested?
- Who or what requested it?
- Which notes or tags were affected?
- Was the operation low, medium, or high risk?
- Did the user need to confirm it?
- Can the user inspect what happened afterward?
- Can the user undo or recover from the operation?
- Did the operation write all related state atomically?

The output of Phase 2 is a trustworthy command path:

```text
Manual UI / Local tool simulator / Future MCP tool
    -> NoteCommandService
        -> RiskPolicy
        -> Confirmation / Undo / Revision / CommandLog
        -> Existing Note UseCases
            -> Repository
                -> Room
```

## 2. Product Contract

From the user's point of view, Phase 2 must satisfy these promises:

- High-impact note operations do not happen silently.
- Destructive or broad operations explain what will change before execution.
- Recent operations can be inspected in a human-readable way.
- A note modified or deleted by an automated entry point can be traced back to the operation that caused it.
- Recoverable operations provide either undo or revision restore.
- Failed operations leave enough information to understand what happened without exposing internal stack traces.

From the developer's point of view, Phase 2 must satisfy these constraints:

- Future voice/MCP tools must not call repositories or DAOs directly.
- Risk classification is centralized and testable.
- Confirmation state is represented as data, not scattered UI flags.
- Command logs are written for local tool simulation and future assistant-triggered operations.
- Revisions are written before content-destructive mutations.
- Multi-step operations that change notes and write logs must use a transaction or equivalent consistency boundary.
- Pending confirmations are persisted in Room, not kept only in memory.
- Command concepts are represented by strong domain types, not raw strings spread through the codebase.
- Phase 2 must not require real network, microphone, wake word, or Xiaozhi runtime.

## 3. Relationship to Phase 1

Phase 1 owns:

- Manual note creation, editing, deletion, restoration, archive, pin, todo, tag, search, filter, and UI polish.
- UseCase boundaries for note writes.
- A usable local-first notes app.

Phase 2 owns:

- Operation metadata.
- Risk policy.
- Confirmation workflow.
- Revision snapshots.
- Command logs.
- Undo contracts.
- Local tool-call simulator.
- Developer-facing verification that future voice/MCP calls will use the same business path.

If a feature is already implemented in Phase 1, Phase 2 must reuse it through UseCases instead of reimplementing it.

## 4. Included

- `note_revisions` table and repository access.
- `assistant_command_log` table and repository access.
- `pending_confirmations` table and repository access.
- `NoteCommandService`.
- `NoteRiskPolicy`.
- `PendingConfirmationStore` production behavior.
- `CommandTraceRepository` or `NoteCommandTransactionRepository` domain interface with a Room transaction-backed data implementation.
- `UiCommandBus` confirmation and result events.
- Local tool-call simulator screen or debug panel.
- Structured command result model.
- Structured error model for command operations.
- Strong command domain model: `CommandSource`, `RiskLevel`, `ConfirmationStatus`, `CommandStatus`, `CommandErrorCode`, and `ToolName`.
- `SearchNotesUseCase` or equivalent reusable domain/service search path shared by UI, simulator, and future MCP tools.
- Revision creation before destructive or overwriting mutations.
- Command log creation for simulated tools and future assistant commands.
- Confirmation workflow for high-risk operations.
- Undo workflow for selected medium-risk operations.
- Tests for risk classification, revision writing, confirmation, command log status, and transactional integrity.

## 5. Excluded

- Real voice assistant runtime.
- OTA activation.
- WebSocket conversation.
- Audio recording and playback.
- Wake word foreground service behavior.
- Cloud sync.
- Cross-device conflict resolution.
- Full revision history product UI, except minimal inspection or restore entry if needed for validation.
- Natural language understanding. Phase 2 accepts already-normalized tool calls.

## 6. Terms

- `Command`: A normalized request to perform an operation, such as `notes.delete` or `notes.append`.
- `Command source`: The entry point that requested the command: `manual`, `local_tool_simulator`, `voice`, `wakeword`, `system`, or `import`.
- `Risk level`: `low`, `medium`, or `high`.
- `Confirmation`: A user approval required before executing a high-risk operation.
- `Pending confirmation`: A stored command that is waiting for user approval.
- `Command log`: Persistent record of a command request, status, inputs, affected entities, and result.
- `Revision`: Snapshot of a note before a destructive or overwriting mutation.
- `Undo`: A short-lived reversal action for selected completed commands.
- `Tool simulator`: A local debug UI that calls command/tool handlers without real voice or network.

## 7. Global Rules

### 7.1 Command Path

- All Phase 2 command-like operations must enter through `NoteCommandService` or an equivalent command boundary.
- `NoteCommandService` may call existing Phase 1 UseCases.
- `NoteCommandService` must not call DAO directly.
- Local tool simulator must use the same command/tool handlers that future MCP tools will use.
- UI may continue to use Phase 1 UseCases for ordinary manual operations, but any operation that needs risk, confirmation, log, undo, or revision must use the command boundary.
- Search behavior must not live only in `notes-ui`. `notes.search`, manual search, and future voice search must share a reusable `SearchNotesUseCase` or domain/service search path.
- Command handlers must use strong domain types internally. Database values may be stored as strings, but command services must not branch on ad hoc string literals throughout the code.

### 7.1.1 Strong Command Types

Phase 2 must define domain-level types for command state and identity:

```text
CommandSource:
- manual
- local_tool_simulator
- voice
- wakeword
- import
- system

RiskLevel:
- low
- medium
- high

ConfirmationStatus:
- not_required
- pending
- confirmed
- rejected
- expired

CommandStatus:
- success
- failed
- blocked
- requires_confirmation
- partial_success

CommandErrorCode:
- invalid_json
- validation_error
- not_found
- already_deleted
- already_archived
- conflict
- requires_confirmation
- confirmation_expired
- confirmation_rejected
- storage_error
- unsupported_tool
- partial_failure

ToolName:
- notes.create
- notes.search
- notes.list_recent
- notes.append
- notes.update_title
- notes.replace_content
- notes.toggle_done
- notes.pin
- notes.archive
- notes.delete
- notes.restore
- notes.restore_revision
- tags.search
- tags.bind
- tags.delete
- tags.create
- tags.rename
```

Rules:

- Room entities may persist enum values as strings.
- Mappers must convert between persisted strings and domain types at the data boundary.
- Unknown persisted values must map to a safe fallback or explicit parse error, not crash the app.
- Command tests must assert domain enum values, not string spelling.

### 7.2 Risk Rules

Minimum required risk levels:

| Operation | Default risk |
| --- | --- |
| `notes.search` | Low |
| `notes.list_recent` | Low |
| `notes.get` | Low |
| `tags.search` | Low |
| `ui.open_note` | Low |
| `notes.create` | Medium |
| `notes.append` | Medium |
| `notes.update_title` | Medium |
| `notes.toggle_done` | Medium |
| `notes.pin` | Medium |
| `notes.archive` | Medium |
| `tags.create` | Medium |
| `tags.bind.add` | Medium |
| `notes.delete` | High |
| `notes.batch_delete` | High |
| `notes.replace_content` | High |
| `notes.batch_update_tags.replace` | High |
| `notes.clear_done` | High |
| `tags.delete` | High |
| `tags.rename` | High when linked notes exist |
| `notes.restore_revision` | High |

Risk escalation rules:

- A medium operation affecting more than 5 notes becomes high risk.
- Any operation that can hide, delete, overwrite, or remove user-visible content is high risk unless explicitly listed otherwise.
- Any future voice or wake-word request that targets an ambiguous note must require clarification or confirmation before mutation.
- Manual single-note UI deletion may remain a direct Phase 1 flow, but command-style deletion from simulator or future assistant must be high risk.

### 7.3 Confirmation Rules

- High-risk commands must not mutate notes unless `confirmed=true` or an approved pending confirmation is consumed.
- A high-risk command without confirmation returns `requires_confirmation`.
- The response must include a user-readable summary and preview of affected notes or tags.
- Pending confirmations expire.
- Pending confirmations must be persisted in Room through `pending_confirmations`.
- Confirming an expired confirmation must fail with an `expired` status and no mutation.
- Rejecting a confirmation must write or update command log status as `rejected`.
- Confirmation IDs must be unique and hard to guess.
- Process death or app restart must not silently execute or lose a still-valid pending confirmation.

### 7.4 Revision Rules

Revision must be written before:

- Replacing note content.
- Updating note title through command path.
- Appending note content through command path, unless the product explicitly treats append as non-destructive. Phase 2 should still record revision for assistant-sourced append.
- Soft deleting note.
- Archiving note through command path.
- Batch modifying notes.
- Restoring a previous revision.
- Replacing tags.
- Future voice-triggered modification.

Revision may be skipped for:

- Pure search/read operations.
- Creating a new note.
- Opening UI.
- Idempotent no-op operations where no persisted note changes.

### 7.5 Command Log Rules

- A command log must be written for every local tool simulator call.
- Future voice/MCP tool calls must use the same logging path.
- Manual UI actions that do not go through command path do not need command logs in Phase 2.
- Failed and blocked commands must be logged.
- Commands requiring confirmation must create or update a log with `requires_confirmation` or `pending`.
- Confirmed commands must be linked to the original pending command when possible.
- Log entries must store arguments and results as JSON strings, but UI must display safe summaries.

### 7.6 Transaction Rules

For a mutation command, these steps must be atomic where possible:

```text
create command log
create revision snapshots if required
perform note/tag mutation
update command log with result and affected ids
```

If the mutation fails:

- The command log must end as `failed`.
- Partial note changes must be rolled back when the storage layer supports a transaction.
- If rollback is not possible, the log must clearly mark partial failure and affected ids.

Required design:

- Define a domain interface such as `CommandTraceRepository` or `NoteCommandTransactionRepository`.
- Implement that interface in `notes-data` using `RoomDatabase.withTransaction`.
- `NoteCommandService` calls the transaction interface; it must not call DAO directly.
- The transaction interface is responsible for command log creation/update, revision insertion, pending confirmation persistence/consumption, and note/tag mutation coordination.
- Existing Phase 1 UseCases may remain the source of business validation, but Phase 2 mutation commands must not rely on a loose sequence of independent UseCase calls when atomicity is required.

## 8. Data Contract

### 8.1 `note_revisions`

Required fields:

```text
id: Long
note_id: Long
title_snapshot: String
content_snapshot: String
tags_snapshot_json: String
type_snapshot: String
is_done_snapshot: Boolean
pinned_snapshot: Boolean
archived_snapshot: Boolean
deleted_snapshot: Boolean
color_snapshot: String?
created_at: Long
source: manual | local_tool_simulator | voice | wakeword | import | system
reason: String?
command_log_id: Long?
```

Rules:

- Snapshot fields represent note state before mutation.
- `created_at` is revision creation time, not original note creation time.
- `command_log_id` is nullable for manual UI revision paths, but required for command path revisions when a command log exists.
- Restoring a revision must itself create a new revision of current state before overwriting the note.

### 8.2 `assistant_command_log`

Required fields:

```text
id: Long
conversation_id: String?
source: manual | local_tool_simulator | voice | wakeword | import | system
user_text: String?
recognized_text: String?
normalized_intent: String?
tool_name: String
arguments_json: String
risk_level: low | medium | high
confirmation_status: not_required | pending | confirmed | rejected | expired
result_json: String?
affected_note_ids_json: String?
affected_tag_ids_json: String?
status: success | failed | blocked | requires_confirmation | partial_success
error_code: String?
error_message: String?
created_at: Long
completed_at: Long?
```

Rules:

- `arguments_json` must contain normalized arguments, not raw UI text only.
- Sensitive or very large payloads should be summarized if needed, but Phase 2 note text can be stored locally.
- `result_json` must be machine-readable enough for debug and tests.
- `error_message` must be safe for users and must not include stack traces.

### 8.3 `pending_confirmations`

Pending confirmations must be persisted in a Room table. Required fields:

```text
confirmation_id: String
command_log_id: Long
tool_name: String
arguments_json: String
risk_level: high
preview_json: String
created_at: Long
expires_at: Long
source: local_tool_simulator | voice | wakeword | system
status: pending | confirmed | rejected | expired
```

Rules:

- In-memory-only pending confirmation storage is not acceptable for completed Phase 2.
- `PendingConfirmationStore` may keep a memory cache, but Room is the source of truth.
- Expired confirmations must not execute.
- Confirmation lookup must validate both `confirmation_id` and status.
- Confirm/reject must update both `pending_confirmations` and the related command log.
- On app startup or command-log viewer load, expired pending confirmations should be marked `expired`.

## 9. UI Specification

### 9.1 Local Tool Simulator

The simulator is a debug/developer UI. It must not look like the normal user note editor.

Minimum capabilities:

- Select or type tool name.
- Edit arguments JSON.
- Execute call.
- Show risk level.
- Show status.
- Show message.
- Show result JSON.
- Show affected note ids.
- Show command log id.
- For `requires_confirmation`, show confirmation preview and allow confirm/reject.

Required simulator tools for Phase 2:

- `notes.create`
- `notes.search`
- `notes.list_recent`
- `notes.append`
- `notes.update_title`
- `notes.replace_content`
- `notes.toggle_done`
- `notes.pin`
- `notes.archive`
- `notes.delete`
- `tags.search`
- `tags.bind`
- `tags.delete`

Optional but useful:

- `notes.restore`
- `notes.restore_revision`
- `tags.create`
- `tags.rename`

### 9.2 Confirmation UI

Confirmation UI must show:

- Operation title.
- Human-readable risk summary.
- Affected note count.
- Preview list for affected notes when available.
- Confirm action.
- Reject/cancel action.

Rules:

- Confirm and reject actions must be visually distinct.
- Confirming must execute only the pending command represented by the confirmation id.
- Rejecting must not mutate notes.
- If the target notes changed while confirmation was pending, the command must revalidate before mutation.

### 9.3 Command Log Viewer

Phase 2 must provide at least one way to inspect recent command logs.

Minimum display:

- Created time.
- Source.
- Tool name.
- Risk level.
- Status.
- Confirmation status.
- Message or error.
- Affected note ids.

The viewer may be in a debug/settings area.

### 9.4 Revision Viewer or Restore Entry

Phase 2 does not need a polished full revision history product UI, but it must provide enough access to validate revision behavior.

Minimum acceptable options:

- A debug revision list for a note.
- Or a note detail entry showing latest revision and restore action.
- Or tests proving revision creation and restore behavior if UI is deferred.

## 10. Command Behavior Cards

### CMD-01 `notes.create`

- Risk: Medium.
- Confirmation: Not required.
- Revision: Not required.
- Log: Required for simulator/future tool path.
- Normal flow:
  1. Validate title, content, type, tags, color.
  2. Create command log with `status=blocked` or `pending_execution` equivalent if such status exists, otherwise create before mutation and update afterward.
  3. Call Phase 1 create UseCase.
  4. Update command log with `success` and new note id.
  5. Return created note id and message.
- Negative paths:
  - Invalid type returns validation error.
  - Empty title and content follow Phase 1 rules.
- Acceptance:
  - Given valid arguments, When simulator calls `notes.create`, Then a note appears in the list and a command log exists.

### CMD-02 `notes.search`

- Risk: Low.
- Confirmation: Not required.
- Revision: Not required.
- Log: Required for simulator/future tool path.
- Normal flow:
  1. Validate query and filters.
  2. Search through `SearchNotesUseCase` or an equivalent reusable domain/service path.
  3. Return limited results with id, title, snippet, tags, and updated time.
- Negative paths:
  - Invalid JSON returns `failed` with validation error.
- Acceptance:
  - Given deleted notes match the query, When `include_deleted=false`, Then deleted notes do not appear.
  - Given the same query is entered in manual UI search and simulator `notes.search`, Then both paths use the same search rules and return compatible ordering.

### CMD-03 `notes.append`

- Risk: Medium.
- Confirmation: Not required by default.
- Revision: Required for command path.
- Log: Required.
- Normal flow:
  1. Validate note exists and is not deleted.
  2. Create command log.
  3. Snapshot current note into revision.
  4. Append content using note UseCase or command-specific UseCase.
  5. Update command log with success.
  6. Return affected note id.
- Negative paths:
  - Missing note returns not found.
  - Empty append content returns validation error.
- Acceptance:
  - Given a note content is `A`, When appending `B`, Then content becomes `A\nB` or the documented join format, and a revision stores `A`.

### CMD-04 `notes.update_title`

- Risk: Medium.
- Confirmation: Not required by default.
- Revision: Required.
- Log: Required.
- Normal flow:
  1. Validate note exists.
  2. Snapshot current note.
  3. Update title.
  4. Log affected note id.
- Acceptance:
  - Given title `旧标题`, When updated to `新标题`, Then revision contains `旧标题`.

### CMD-05 `notes.replace_content`

- Risk: High.
- Confirmation: Required.
- Revision: Required after confirmation, before mutation.
- Log: Required.
- Normal flow:
  1. Without confirmation, return `requires_confirmation` with old/new content preview.
  2. Store pending confirmation.
  3. On confirmation, revalidate note exists and content preview still matches or handle conflict.
  4. Snapshot current note.
  5. Replace content.
  6. Update log with success.
- Negative paths:
  - Expired confirmation returns `expired`.
  - If note changed since preview, return `blocked` with conflict message.
- Acceptance:
  - Given unconfirmed replace request, Then note content is unchanged.
  - Given confirmed replace request, Then content is replaced and revision stores old content.

### CMD-06 `notes.toggle_done`

- Risk: Medium.
- Confirmation: Not required.
- Revision: Optional for manual path, required for command path if source is voice/wakeword. For simulator, recommended.
- Log: Required.
- Normal flow:
  1. Validate note exists and type is todo.
  2. Toggle or set requested done state.
  3. Log success.
- Negative paths:
  - Normal note returns validation error.
- Acceptance:
  - Given a normal note id, When simulator calls toggle done, Then status is failed and note is unchanged.

### CMD-07 `notes.pin`

- Risk: Medium.
- Confirmation: Not required unless batch size exceeds threshold.
- Revision: Optional for single-note pin; required for batch command path.
- Log: Required.
- Normal flow:
  - Set pin state using existing UseCase and log affected ids.
- Acceptance:
  - Given a note is unpinned, When `pinned=true`, Then it appears in pinned filter and command log records success.

### CMD-08 `notes.archive`

- Risk: Medium for small batch, high for batch size above threshold.
- Confirmation: Required when high.
- Revision: Required for command path.
- Log: Required.
- Normal flow:
  - Archive or unarchive selected notes.
  - Exclude deleted notes or return validation error depending on command arguments.
- Acceptance:
  - Given three active notes, When archived, Then they disappear from active list and appear in archived filter.

### CMD-09 `notes.delete`

- Risk: High.
- Confirmation: Required.
- Revision: Required after confirmation, before mutation.
- Log: Required.
- Normal flow:
  1. Validate note ids.
  2. If not confirmed, create pending confirmation and return preview.
  3. On confirm, revalidate target notes.
  4. Snapshot each target note.
  5. Soft delete each target note.
  6. Update command log with affected ids.
- Negative paths:
  - Empty note id list returns validation error.
  - Already deleted notes are skipped or reported as conflict. The chosen rule must be consistent.
  - Expired confirmation must not delete.
- Acceptance:
  - Given delete is requested without confirmation, Then no notes are deleted.
  - Given confirmed delete, Then notes move to recently deleted and revisions exist for each note.

### CMD-10 `tags.bind`

- Risk: Medium for add/remove, high for replace.
- Confirmation: Required for replace or batch threshold.
- Revision: Required when existing note tags are replaced.
- Log: Required.
- Normal flow:
  - Add, remove, or replace tags through tag/note UseCase.
  - Log affected note id and tag ids or tag names.
- Acceptance:
  - Given a note has tag `客户`, When replace with `报价`, Then revision stores original tags.

### CMD-11 `tags.delete`

- Risk: High.
- Confirmation: Required.
- Revision: Required for every affected note before relationships are removed.
- Log: Required.
- Normal flow:
  1. Validate tag exists.
  2. Count linked notes.
  3. Without confirmation, return `requires_confirmation` with tag name and affected note count.
  4. On confirmation, snapshot each affected note's current tag state.
  5. Remove note-tag relationships.
  6. Delete the tag entity.
  7. Update command log with affected note ids and tag id.
- Negative paths:
  - Missing tag returns `not_found`.
  - Expired confirmation must not delete the tag.
  - If linked notes changed while pending, revalidate and either update preview or block with conflict.
- Acceptance:
  - Given tag `客户` is linked to 3 notes, When delete is requested without confirmation, Then no tag relationship changes.
  - Given tag delete is confirmed, Then notes remain visible and the tag no longer appears on those notes.
  - Given tag delete is confirmed, Then command log records high risk and revisions exist for affected notes.

### CMD-12 `notes.restore_revision`

- Risk: High.
- Confirmation: Required.
- Revision: Required before restore overwrites current state.
- Log: Required.
- Normal flow:
  1. Validate note and revision exist.
  2. Return confirmation preview comparing current and target revision.
  3. On confirmation, snapshot current state.
  4. Restore revision fields.
  5. Log success.
- Acceptance:
  - Given current content `B` and revision content `A`, When restore is confirmed, Then current content becomes `A` and a new revision stores `B`.

## 11. Error Model

Minimum command error codes are represented by the `CommandErrorCode` domain type:

```text
invalid_json
validation_error
not_found
already_deleted
already_archived
conflict
requires_confirmation
confirmation_expired
confirmation_rejected
storage_error
unsupported_tool
partial_failure
```

Rules:

- Tool result status must be one of `success`, `failed`, `blocked`, `requires_confirmation`, or `partial_success`.
- User-facing messages must be concise and localized or ready for localization.
- Developer details may be logged locally, but stack traces must not be shown in command result messages.
- String values in JSON and Room must round-trip through domain enum/sealed types.

## 12. Concurrency and Conflict Rules

Phase 2 must prepare for multiple entry points even before real voice exists.

- Pending confirmation must revalidate target state before executing.
- If a note changed after confirmation preview was created, high-risk overwrite operations must block and ask the user to retry.
- Single-property operations such as pin or done toggle may proceed if target note still exists and is not deleted.
- Batch operations should report skipped notes if some targets are no longer valid.
- Command logs must identify `partial_success` if only part of a batch succeeds.

Recommended implementation:

- Add `updated_at` precondition to confirmation preview for conflict detection.
- Add `expected_updated_at` to high-risk command arguments or pending confirmation payload.
- Consider a future `version` column before Phase 4 if `updated_at` is not reliable enough.

## 13. Test Acceptance Suite

Minimum tests for Phase 2:

- RiskPolicy: classifies each supported command with expected risk.
- RiskPolicy: escalates medium batch operations above threshold to high.
- Confirmation: high-risk command without confirmation does not mutate notes.
- Confirmation: confirmed command mutates only after consuming valid pending confirmation.
- Confirmation: expired confirmation does not mutate notes.
- CommandLog: successful command writes `success`, affected ids, result JSON, and completed time.
- CommandLog: failed validation writes `failed` with error code.
- Revision: replace content writes old snapshot before mutation.
- Revision: delete writes snapshot for each deleted note.
- Revision: restore revision writes current state before restore.
- Transaction: if mutation fails after revision creation, no orphan success log is left.
- Simulator: `notes.create` creates note and log.
- Simulator/Search: manual UI search and simulator `notes.search` share the same search UseCase or service.
- Simulator: `notes.delete` returns `requires_confirmation` before deleting.
- Simulator: confirm delete moves note to recently deleted and creates revision.
- Simulator: `tags.delete` returns `requires_confirmation`, then confirmed deletion removes only tag relationships and the tag itself.
- PendingConfirmation: pending confirmations survive app process restart while unexpired.
- PendingConfirmation: expired rows are marked expired and cannot execute.
- Architecture: command handlers do not import DAO classes.
- Architecture: assistant tool layer calls command boundary or UseCases, not Room directly.
- Architecture: transaction-backed command trace repository is implemented in data layer and exposed through a domain interface.

## 14. Phase Completion Definition

Phase 2 is complete when:

- The app still works as the Phase 1 manual notes app.
- Command path exists for key note operations.
- Pending confirmations are persisted in Room and survive restart until expiration.
- High-risk command operations require confirmation.
- Revision snapshots are created for destructive or overwriting command operations.
- Revision snapshots include title, content, tags, type, done state, pinned state, archived state, deleted state, and color.
- Command logs are created for simulator calls and are inspectable.
- Local tool simulator can execute and verify the required tools without real voice/network.
- `tags.delete` is implemented as a required high-risk simulator command.
- Search logic used by simulator/future tools is shared with manual UI through a reusable UseCase or domain/service path.
- Command status, source, risk, confirmation status, error code, and tool names are represented by strong domain types.
- Confirmation reject, expiration, conflict, and success paths are tested.
- Batch operation partial failure behavior is defined and tested.
- No command handler or future assistant tool directly writes through DAO.
- The project is ready for Phase 3 assistant runtime migration without redesigning note safety.

## 15. Traceability

This spec refines the original Phase 2 section of:

- `docs/DEVELOPMENT_PLAN.md`

It builds on:

- `docs/spec/PHASE1_NOTES_SPEC.md`
- `docs/phase1/PHASE1-05-home-polish-and-gesture-report.md`
- `docs/phase1/PHASE1-05-archive-gesture-and-theme-polish-report.md`

When Phase 2 behavior changes, update this spec in the same commit as the implementation or before implementation begins.
