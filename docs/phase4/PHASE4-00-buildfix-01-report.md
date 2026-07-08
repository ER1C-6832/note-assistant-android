# Phase4-00 buildfix-01 report

## Issue

`:assistant-runtime:kaptGenerateStubsDebugKotlin` failed after Phase4-00 interface cleanup.

## Root cause

`McpToolStatus` moved from the Phase3 runtime-internal MCP model to `assistant-mcp-base`, but `ProtocolEvent.kt` still imported the old runtime package.

## Fix

- `ProtocolEvent.kt` now imports `com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus`.
- Runtime protocol/network tests that assert `McpToolStatus` were aligned with the shared base model.
- No runtime behavior or note mutation behavior was changed.

## Safety

`assistant-runtime` still does not call `NoteCommandService` in Phase4-00.
