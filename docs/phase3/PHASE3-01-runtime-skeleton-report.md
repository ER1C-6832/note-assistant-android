# Phase3-01 Assistant Runtime Skeleton Report

## Scope

This overlay starts Phase3 by creating a compile-time `assistant-runtime` skeleton. It does not connect to the real Xiaozhi service, does not request microphone permission, does not open WebSocket, and does not execute note tools.

## Implemented

- `AssistantState` with phase, connection, activation, audio, status text, error, last user/assistant text, session id, reconnect attempt, and fake runtime marker.
- `AssistantController` public runtime boundary.
- `LocalAssistantController` fake runtime implementation for Gate A development.
- `ConversationStateMachine` deterministic state transitions.
- `XiaozhiMessageBuilder` initial protocol message builders aligned with the audited `xiaozhi-android` message shape.
- `XiaozhiMessageRouter` typed protocol event skeleton.
- `McpProtocolClient` Phase3-safe boundary that blocks `notes.*` and `tags.*` tool calls without importing `NoteCommandService`.
- Audio contracts and fake passthrough recorder/player/Opus interfaces.
- Hilt binding for `AssistantController`.
- Unit tests for state machine, MCP blocking, and protocol routing.

## Safety boundary

`assistant-runtime` does not import `notes-data`, Room DAO, `NoteRepositoryImpl`, or `NoteCommandService`. Phase3 note mutation remains blocked until Phase4.

## Acceptance

Run:

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```

Expected:

- Assistant runtime unit tests pass.
- App still builds and manual notes remain available.
- There is no new microphone permission prompt and no real WebSocket work in Phase3-01.

## Next

Phase3-02 should wire the runtime into settings with clear sections instead of appending runtime controls below Phase2 debug tools.
