# note-assistant-android

Android native note-taking app with voice assistant integration.

This repository is currently a planning and repository skeleton only. It intentionally contains no Android, Kotlin, Gradle, CI, or application source files yet.

## Product Direction

The product is a local-first Android notes app where manual UI actions, voice assistant actions, wake-word background actions, and future shortcuts all go through the same note UseCases.

Core idea:

```text
UI / Voice / Wake word / Future entries
    -> Note UseCases
        -> NoteRepository
            -> Room
```

The voice assistant is an input and automation layer, not a privileged path around the business layer.

## Planned Modules

- `app`
- `core-common`
- `notes-domain`
- `notes-data`
- `notes-ui`
- `assistant-runtime`
- `assistant-mcp-base`
- `assistant-tools`
- `assistant-bridge`
- `assistant-wakeword`
- `app-settings`

## Planning Document

The full development outline is kept in:

- `note-assistant-android-development-plan.md`

## Repository Status

Phase 0 has not started yet. Build files and source code will be added later when implementation begins.
