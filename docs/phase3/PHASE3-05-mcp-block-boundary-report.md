# Phase3-05 MCP Protocol Block Boundary Report

## Scope

Phase3-05 implements the transport-level MCP protocol boundary required by Phase 3.
It supports:

- `tools/list`
- `tools/call`
- safe Phase3-only runtime tools
- blocking all `notes.*` and `tags.*` tool calls
- fake WebSocket MCP request/response simulation for Gate A

## Safety boundary

Phase3 still must not execute real note-changing tools.
This stage does not import or call `NoteCommandService` from `assistant-runtime`.
All note or tag tool calls received through MCP are converted into a blocked MCP response.

Blocked examples:

- `notes.create`
- `notes.delete`
- `notes.replace_content`
- `notes.restore_revision`
- `tags.bind`
- `tags.delete`

## Implemented files

```text
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/mcp/McpProtocolClient.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/protocol/ProtocolEvent.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/protocol/XiaozhiMessageRouter.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/network/FakeXiaozhiWebSocketClient.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/controller/LocalAssistantController.kt
assistant-runtime/build.gradle.kts
```

## Tests

```text
assistant-runtime/src/test/java/com/er1cmo/noteassistant/assistant/runtime/mcp/McpProtocolClientTest.kt
assistant-runtime/src/test/java/com/er1cmo/noteassistant/assistant/runtime/protocol/XiaozhiMessageRouterTest.kt
assistant-runtime/src/test/java/com/er1cmo/noteassistant/assistant/runtime/network/FakeXiaozhiWebSocketClientTest.kt
```

Covered behavior:

- `tools/list` returns only Phase3-safe runtime tools.
- `tools/call notes.delete` returns blocked MCP response.
- `tools/call tags.delete` returns blocked MCP response.
- `phase3.status` succeeds without touching notes.
- Message router converts incoming `type=mcp` payloads into typed `ProtocolEvent.McpResponse`.
- Fake WebSocket can simulate tools/list and blocked tools/call request/response turns.

## Acceptance

Phase3-05 is complete when:

1. Unit tests pass.
2. Settings fake runtime can simulate a `notes.delete` request and show it is blocked.
3. Manual notes remain unchanged after blocked MCP calls.
4. No `assistant-runtime` code imports `NoteCommandService`, `notes-data`, Room DAO, or repository implementations.

## Still deferred

- Real NoteCommandService tool execution: Phase4.
- Real voice-created notes: Phase4+.
- Push-to-talk permission/audio path: Phase3-06/07.
- Wake word/background listening: Phase5.
