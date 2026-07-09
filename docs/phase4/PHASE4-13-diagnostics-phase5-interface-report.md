# Phase4-13 diagnostics cleanup and Phase5 source interface

## Scope

This overlay prepares the assistant runtime for Phase5 wakeword integration without changing the command execution chain.

## Changes

- Replaced the old `gateBRealToolCallBlockedVerified` runtime concept with `phase4RealToolCallVerified`.
- Kept a deprecated compatibility getter for `gateBRealToolCallBlockedVerified` so older UI/tests do not fail while the source concept is migrated.
- Added runtime diagnostic fields:
  - `lastToolName`
  - `lastToolStatus`
  - `lastCommandLogId`
  - `lastConfirmationId`
- Cleaned runtime status text so real MCP calls are described as Phase4 tool-chain execution, not Phase3 blocking.
- Routed command source through `McpToolContext`:
  - Real Xiaozhi runtime uses `source=voice`.
  - Fake runtime/debug MCP simulation uses `source=local_tool_simulator`.
  - Future wakeword path can pass `source=wakeword` through the same `McpToolContext` without rebuilding tools or command services.
- Extended `McpProtocolClient.handleJsonRpc()` to accept a `McpToolContext` and preserve request id/session/source.
- Updated fake and real WebSocket routers to pass the correct source into MCP execution.

## Architecture boundary

The intended boundary remains:

- `assistant-runtime` depends on MCP base/runtime protocol/network/audio/controller layers only.
- `assistant-runtime` still does not call `NoteCommandService`, Room, DAO, or notes-data directly.
- `assistant-tools` continues to be the boundary that calls note-domain command services.
- Phase5 should only add a wakeword entry point/source and should not reimplement command execution.

## Validation checklist

Run:

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-runtime:assembleDebug :assistant-tools:assembleDebug :app:assembleDebug --no-build-cache
```

Manual checks:

1. Fake runtime debug `tools/call` should create command logs with source `local_tool_simulator`.
2. Real runtime `tools/call` should create command logs with source `voice`.
3. `AssistantState.lastToolName / lastToolStatus / lastCommandLogId / lastConfirmationId` should reflect the most recent MCP tool call.
4. Real MCP status should read as Phase4 tool-chain handling, not Phase3 blocked/tool_block handling.
5. Future wakeword should call the same MCP path with `McpToolContext(source = SOURCE_WAKEWORD)`.
