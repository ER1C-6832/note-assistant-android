# note-assistant-android

`note-assistant-android` is a local-first Android note-taking application with an embedded Xiaozhi-compatible voice assistant. The application is designed so that manual UI actions, voice assistant actions, MCP tool calls, and future wake-word entry points operate through the same note business layer.

The project is written in Kotlin and uses Jetpack Compose, Room, Hilt, DataStore, OkHttp WebSocket, Android audio APIs, and a modular domain-first architecture.

## Status

The project has progressed beyond the initial repository skeleton. The current codebase contains:

- A usable local notes application with create, edit, delete, restore, archive, pin, todo, tag, color, search, filter, grid/list, and settings flows.
- A trusted command layer with risk classification, command logs, pending confirmations, revision snapshots, and recovery-oriented behavior.
- A Xiaozhi-compatible assistant runtime with activation, WebSocket, text interaction, push-to-talk audio, Opus encode/decode path, TTS playback, reconnect/error handling, and MCP routing.
- A Phase 4 MCP tool chain that exposes notes, tags, UI navigation, and confirmation tools through an injected executor.
- A global assistant entry overlay for foreground voice interaction.
- A prepared source boundary for Phase 5 wake-word integration.

Phase 5 wake-word foreground service integration is represented as a module boundary and planning target, but should be treated as ongoing work unless the corresponding implementation reports and runtime tests are present.

## Product Scope

The application is a notes product first. The voice assistant is an input and automation layer, not a privileged path around note business rules.

Core flow:

```text
Manual UI / Voice assistant / MCP / Future wake word
    -> UseCase or trusted command boundary
        -> Repository
            -> Room
```

For assistant-triggered note operations, the expected flow is:

```text
Xiaozhi-compatible runtime
    -> MCP JSON-RPC tools/list or tools/call
        -> assistant-mcp-base executor contract
            -> assistant-tools
                -> NoteCommandService / UiCommandBus
                    -> notes-domain UseCases
                        -> notes-data Room storage
```

## Main Features

### Notes

- Local-first note storage.
- Normal notes and todo notes.
- Create, edit, soft delete, restore, and permanent cleanup paths.
- Archive and unarchive.
- Pin and unpin.
- Todo completion.
- Note colors.
- Tag creation, binding, filtering, rename, and deletion.
- Search and filter behavior shared with the assistant command path.
- Grid/list presentation and UI polish.

### Trust and Traceability

- `NoteCommandService` for normalized tool-style note operations.
- Risk policy for low, medium, and high-risk commands.
- Persistent pending confirmations.
- Command logs for assistant/tool-triggered operations.
- Revision snapshots before destructive or overwriting mutations.
- Confirmation and rejection flows for high-risk operations.
- Transaction-aware trace repository boundary.

### Assistant Runtime

- Fake and real runtime modes.
- OTA/activation support.
- Xiaozhi-compatible WebSocket client.
- `hello`, `listen`, text, audio, and MCP message handling.
- Push-to-talk recording.
- AudioRecord / AudioTrack based audio flow.
- MediaCodec Opus encode/decode path.
- Runtime state model covering activation, connection, audio, and assistant phase.
- Runtime diagnostics for recent tool name, tool status, command log id, confirmation id, protocol JSON, and audio state.

### MCP Tools

The assistant tool surface is registered through `assistant-tools` and exposed through `assistant-mcp-base`.

Notes tools:

```text
notes.search
notes.list_recent
notes.get
notes.create
notes.append
notes.delete
notes.update_title
notes.toggle_done
notes.pin
notes.replace_content
notes.restore_revision
notes.archive
notes.restore
notes.clear_done
notes.list_archived
notes.list_deleted
notes.list_todos
notes.list_done
notes.list_pinned
notes.list_by_tag
```

Tag tools:

```text
tags.search
tags.bind
tags.delete
tags.create
tags.rename
tags.list
```

UI tools:

```text
ui.open_note
ui.show_confirmation
ui.show_search
ui.show_note_list
ui.show_tag
ui.show_archive
ui.show_trash
```

Assistant confirmation tools:

```text
assistant.confirm
assistant.reject
assistant.list_pending_confirmations
```

High-risk tools are expected to return `requires_confirmation` and execute only after a persisted confirmation is consumed.

## Architecture

The repository is split into Gradle modules:

```text
app
core-common
notes-domain
notes-data
notes-ui
assistant-mcp-base
assistant-bridge
assistant-runtime
assistant-tools
assistant-wakeword
app-settings
```

### Module Responsibilities

- `app`: Android application entry point, navigation, global UI command handling, and dependency graph composition.
- `core-common`: Shared primitives such as time abstractions and common utilities.
- `notes-domain`: Note models, repositories, UseCases, command model, risk policy, and `NoteCommandService`.
- `notes-data`: Room database, entities, DAOs, mappers, repository implementations, command logs, revisions, and pending confirmations.
- `notes-ui`: Compose screens for notes, editor, detail, settings, tags, filters, themes, and local debug views.
- `assistant-mcp-base`: MCP tool contracts, descriptors, JSON-RPC mapping, result envelopes, risk types, and executor interfaces.
- `assistant-bridge`: UI command bus and assistant-to-app presentation commands.
- `assistant-runtime`: Xiaozhi-compatible activation, WebSocket, protocol routing, audio runtime, state machine, diagnostics, and reconnection handling.
- `assistant-tools`: Notes, tags, UI, and confirmation MCP tool implementations.
- `assistant-wakeword`: Reserved boundary for foreground wake-word integration.
- `app-settings`: DataStore-backed local settings for notes UI and assistant runtime configuration.

### Boundary Rules

- `assistant-runtime` must not call `NoteCommandService`, Room DAOs, `notes-data`, or repository implementations directly.
- `assistant-tools` is the boundary that translates MCP calls into trusted note commands or UI commands.
- Note mutations from assistant entry points must go through `NoteCommandService` or a documented command boundary.
- UI navigation tools emit `UiCommand` events rather than directly manipulating activities.
- Future wake-word integration should reuse the existing MCP/command path with `source=wakeword`.

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- MVVM
- Coroutines and Flow
- Hilt
- Room / SQLite
- DataStore Preferences
- OkHttp WebSocket
- Android AudioRecord / AudioTrack
- MediaCodec Opus encode/decode
- JUnit tests

## Build

Requirements:

- Android Studio or Android Gradle Plugin compatible command-line environment.
- JDK 17.
- Android SDK with compile SDK 35.

Common commands:

```bat
gradlew.bat :app:assembleDebug
```

Run selected unit tests:

```bat
gradlew.bat :assistant-mcp-base:testDebugUnitTest :assistant-tools:testDebugUnitTest :assistant-runtime:testDebugUnitTest
```

Clean build:

```bat
gradlew.bat clean :app:assembleDebug --no-build-cache
```

## Documentation

Primary design documents:

- `docs/DEVELOPMENT_PLAN.md`
- `docs/spec/PHASE1_NOTES_SPEC.md`
- `docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md`
- `docs/spec/PHASE3_ASSISTANT_RUNTIME_SPEC.md`
- `docs/spec/PHASE4_MCP_NOTES_TOOLS_SPEC.md`

Phase delivery reports are stored under:

```text
docs/phase1
docs/phase2
docs/phase3
docs/phase4
```

These reports describe implementation overlays, build fixes, test additions, and manual acceptance requirements for each development phase.

## Development Phases

### Phase 1: Notes Product

Manual local notes application with CRUD, editor/detail surfaces, tags, search, filters, archive, trash, pin, todo behavior, color selection, and UI polish.

### Phase 2: Trust and Traceability

Command execution boundary, command logs, risk policy, revisions, pending confirmations, simulator/debug support, and recovery-oriented behavior.

### Phase 3: Assistant Runtime

Xiaozhi-compatible runtime with activation, WebSocket, text, push-to-talk audio, Opus path, playback, diagnostics, and MCP protocol routing. Note mutations were intentionally blocked during this phase.

### Phase 4: MCP Notes Tools

Real notes, tags, UI, and confirmation tools are registered through MCP and routed through the trusted command path. The phase also introduces a foreground assistant entry and diagnostics needed for future wake-word operation.

### Phase 5: Wake Word

Planned integration of a foreground microphone service and local wake-word detection. This phase should reuse the existing assistant runtime and command source boundary rather than introducing a separate note mutation path.

## Safety Model

Tool operations are classified by risk:

- Low-risk operations include search, list, get, and UI navigation.
- Medium-risk operations include create, append, update title, pin, archive, todo state changes, and tag binding where content is not overwritten.
- High-risk operations include delete, content replacement, revision restore, destructive tag changes, clearing completed todos, and large or ambiguous batch operations.

High-risk operations must produce a confirmation request and must not mutate notes until confirmed. Confirmed destructive operations are expected to write revision snapshots before mutation and command logs after completion or failure.

## Repository Notes

- The app is local-first and does not currently implement cloud sync.
- The assistant tool surface is scoped to notes, tags, UI navigation, and confirmation. It is not intended to become a general Android automation assistant.
- Some reports include manual acceptance checklists for real Xiaozhi endpoint validation. Those checks are separate from local fake-runtime and unit-test validation.
