# Phase3-04 WebSocket + Text Conversation Report

## Scope

Phase3-04 adds the WebSocket/text conversation layer required before real audio migration:

- `XiaozhiConnectionConfig`
- real `XiaozhiWebSocketClient` based on OkHttp
- `FakeXiaozhiWebSocketClient` for Gate A validation
- `XiaozhiWebSocketEvent` typed event boundary
- updated `XiaozhiMessageBuilder` with injectable constructor
- `LocalAssistantController` wired to the fake WebSocket path
- tests for message builder, router, and fake WebSocket text conversation

## Protocol alignment

The implementation follows the `xiaozhi-android` reference shape:

- WebSocket request headers:
  - `Authorization: Bearer <token>`
  - `Protocol-Version: 1`
  - `Device-Id`
  - `Client-Id`
- On open, the client sends `hello`.
- The server `hello` must provide `session_id`.
- Text input is sent as `listen/detect` with `text`.
- Binary audio is reserved for Phase3-06/07.

## Safety boundary

This stage still does not:

- request microphone permission,
- start real audio capture,
- call `NoteCommandService`,
- read/write note repositories or DAOs,
- execute note-changing MCP tools.

`McpProtocolClient` remains the Phase3 block boundary for `notes.*` and `tags.*` tool calls.

## Acceptance

Gate A can now validate:

1. Enable assistant.
2. Run fake activation.
3. Connect Fake WebSocket.
4. Confirm hello/session state is visible.
5. Send text.
6. Confirm outgoing JSON uses `listen/detect` and response updates assistant text.
7. Simulate `notes.delete` and confirm it is blocked.
8. Confirm notes remain unchanged.

## Build

Run:

```bash
./gradlew clean :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```
