# Phase2-02 Command Model and Risk Policy Report

## Scope

This overlay continues Phase 2 without introducing real voice, WebSocket, microphone, wake word, or Xiaozhi runtime behavior.

It implements the command domain foundation required by `docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md`:

- strong command domain types
- command result skeleton
- centralized risk policy
- shared search UseCase foundation
- trace data repository and migration files needed when Phase2-01 files are not already present locally

## Architecture audit

A repository search for direct Room/DAO access from UI found no `notes-ui` imports of Room, DAO, or `notes-data` classes. Current UI write paths observed in `NoteListViewModel` continue to call `NoteUseCases` rather than DAOs.

## Added domain types

Package:

```text
notes-domain/.../command
```

Types:

```text
CommandSource
RiskLevel
ConfirmationStatus
CommandStatus
CommandErrorCode
ToolName
TagBindMode
CommandRiskInput
CommandResult
```

Storage values remain string-compatible with the Phase2 spec, but command-facing code can now branch on strong domain values.

## Risk policy

`NoteRiskPolicy` classifies supported tools according to the Phase2 spec.

Implemented rules:

- read/search/open commands are low risk
- create/append/update title/toggle done/pin/archive/tag create/tag bind add-remove are medium risk
- replace content/delete/restore revision/tag delete are high risk
- tag rename becomes high risk when linked notes exist
- tag bind replace is high risk
- medium operations affecting more than five notes escalate to high risk
- unsupported tools are treated as high risk

## Search path

`SearchNotesUseCase` was added so search behavior can move out of UI-only helpers and be shared by manual UI, local tool simulator, and future MCP tools.

It currently supports:

- title/content/tag search
- default sort compatible with Phase1 ordering
- simple pinyin and initial matching foundation
- result scores and matched fields

UI migration to this shared search path can be done in the simulator phase or as a small follow-up polish patch.

## Data layer included for continuity

This overlay also includes the Phase2 trace tables and repository files so Phase2-02 can be applied safely even if a local checkout missed the Phase2-01 overlay:

- `note_revisions`
- `assistant_command_log`
- `pending_confirmations`
- `CommandTraceRepository`
- `CommandTraceRepositoryImpl`
- Room migration `MIGRATION_2_3`

## Not included

- `NoteCommandService`
- local tool simulator UI
- confirmation execution flow
- revision restore behavior
- real assistant runtime

These belong to the next Phase2 steps.
