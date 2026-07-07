# Phase2-03 Buildfix 01

## Problem

Local build failed at `:notes-domain:kaptDebugKotlin` because `NoteCommandService` injects `TimeProvider`, but the `notes-domain` module did not depend on `:core-common`.

The same compilation path would also fail later because `NoteCommandService` calls `noteUseCases.searchNotes(...)`, while `NoteUseCases` did not expose `SearchNotesUseCase` after Phase2-02/03 overlays.

## Fix

- Added `implementation(project(":core-common"))` to `notes-domain/build.gradle.kts`.
- Added `val searchNotes: SearchNotesUseCase` to `NoteUseCases`.

## Scope

This is a build-only fix. It does not change Phase2 command behavior, UI behavior, Room schema, or root README.
