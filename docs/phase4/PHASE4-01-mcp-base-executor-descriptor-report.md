# Phase4-01 MCP Base Executor / Descriptor Skeleton Report

## Status

Phase4-01 prepares the shared MCP foundation required by the Phase4 specification. It does not execute real note tools yet.

## Scope

Implemented in `assistant-mcp-base`:

- `McpTool`
- `McpToolDescriptor`
- `McpRiskLevel`
- `McpToolStatus`
- `McpToolResult`
- `McpToolContext`
- `McpToolExecutor`
- `McpToolRegistry`
- `FailClosedMcpToolExecutor`
- `ToolArgumentParser`
- `McpResultMapper`
- `McpJsonRpcMapper`

## Result envelope

`McpToolResult` now exposes the stable Phase4 envelope fields:

- `status`
- `message`
- `risk`
- `requires_confirmation`
- `confirmation_id`
- `confirmation_summary`
- `confirmation_preview`
- `expires_at`
- `command_log_id`
- `affected_note_ids`
- `affected_tag_ids`
- `result`
- `error_code`
- `tool_name`
- `arguments`

## JSON-RPC mapping

`McpJsonRpcMapper` provides shared handling for:

- `tools/list`
- `tools/call`
- invalid JSON parse errors
- missing tool names
- unsupported methods
- fail-closed executor behavior

The runtime can keep its Phase3 block boundary until Phase4-02 wires an injected executor into the real fake/real runtime path.

## Tests

Added unit tests in `assistant-mcp-base`:

- `McpToolDescriptorTest`
- `McpToolResultTest`
- `McpToolRegistryTest`
- `McpJsonRpcMapperTest`

## Safety boundary

This phase does not call `NoteCommandService`, Room, DAO, or `notes-data`. Real notes/tags execution remains Phase4 Gate A and later.

## Follow-up

Recommended next step:

- Phase4-02: inject `McpToolExecutor` into `assistant-runtime` and route fake/real runtime MCP `tools/list` and `tools/call` through the shared executor, while still failing closed when no real tool implementation is available.
