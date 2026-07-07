# Phase2-01 Trace Data and Migration Report

## Goal

Add the trusted-operation storage foundation required by Phase 2 without changing the existing Phase 1 manual notes behavior.

## Implemented

### Domain

Added strong command domain types:

- `CommandSource`
- `RiskLevel`
- `ConfirmationStatus`
- `CommandStatus`
- `CommandErrorCode`
- `ToolName`

Added trace domain models:

- `NoteRevision`
- `AssistantCommandLog`
- `PendingConfirmation`

Added repository boundary:

- `CommandTraceRepository`

The repository includes a `runInTraceTransaction` method so later command services can use a data-layer Room transaction without importing DAOs.

### Data

Added Room entities:

- `NoteRevisionEntity`
- `AssistantCommandLogEntity`
- `PendingConfirmationEntity`

Added DAOs:

- `NoteRevisionDao`
- `AssistantCommandLogDao`
- `PendingConfirmationDao`

Added mappers:

- `CommandTraceMapper`

Added repository implementation:

- `CommandTraceRepositoryImpl`

### Database migration

Updated `NoteDatabase` from version 2 to version 3.

Added `MIGRATION_2_3` creating:

- `note_revisions`
- `assistant_command_log`
- `pending_confirmations`

The migration also creates indices for note ids, command log ids, creation time, tool name, status, source, expiration, and pending status.

### DI

Updated Hilt modules:

- `DatabaseModule` now provides the new DAOs.
- `RepositoryModule` now binds `CommandTraceRepositoryImpl` to `CommandTraceRepository`.

## Not included yet

This step intentionally does not implement:

- `NoteCommandService`
- `NoteRiskPolicy`
- simulator UI
- command execution
- confirmation execution
- revision restore UI

Those belong to later Phase2 steps after the storage foundation is in place.

## Validation notes

The overlay structure was created and zip-tested. Android compilation was not run in this environment.
