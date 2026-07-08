# Phase4-02 Runtime Executor Fail-Closed Report

## Status

Implemented as an overlay for Phase4-02.

## Scope

Phase4-02 connects `assistant-runtime` MCP parsing to the `assistant-mcp-base` executor contracts without enabling real notes mutation tools yet.

## Implemented

- `McpProtocolClient` no longer hard-codes Phase3-only safe tools.
- `tools/list` is backed by the active `McpToolExecutor`.
- `tools/call` is delegated to the active `McpToolExecutor`.
- If no executor is injected, runtime uses `FailClosedMcpToolExecutor`.
- Missing executor returns blocked `executor_unavailable` and does not mutate notes.
- `initialize` and `notifications/initialized` remain supported for Xiaozhi/MCP handshake compatibility.
- Fake runtime and real Xiaozhi runtime continue to share the same `XiaozhiMessageRouter -> McpProtocolClient` path.
- Added a Hilt multibinds module so the runtime can receive zero or more `McpToolExecutor` providers without depending on `assistant-tools`.

## Safety boundary

- `assistant-runtime` still does not import `NoteCommandService`.
- `assistant-runtime` still does not import `notes-data`, Room DAO, `NoteRepositoryImpl`, or storage implementation classes.
- Phase4 real tool execution is not enabled in this step.
- Notes/tags tool calls fail closed unless a Phase4 executor is injected in a later step.

## Tests updated

- `McpProtocolClientTest`
- `XiaozhiMessageRouterTest`
- `FakeXiaozhiWebSocketClientTest`

## Expected build command

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```

## Acceptance

- `tools/list` no longer returns `phase3.echo` or `phase3.status` when no executor is injected.
- `tools/call notes.delete` returns `blocked` with `executor_unavailable` when no executor is injected.
- A fake injected executor can provide descriptors and execute a fake tool in tests.
- Fake and real runtime paths both use the same `McpProtocolClient` through the message router.
