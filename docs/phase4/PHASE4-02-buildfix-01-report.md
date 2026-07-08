# Phase4-02 Buildfix 01 Report

## Issue

Local build failed at `:assistant-runtime:kaptDebugKotlin` after Phase4-02.

Root causes:

1. `McpExecutorModule` declared an abstract set provider without a Dagger binding annotation, so Hilt treated it as an unimplemented abstract method.
2. `McpProtocolClient` had two `@Inject` constructors: one for the executor set and one no-arg constructor used by tests.

## Fix

- Replaced `McpExecutorModule` with an abstract Hilt module using `@Multibinds` for `Set<McpToolExecutor>`.
- Kept only the executor-set constructor annotated with `@Inject`.
- Preserved a non-injected no-arg constructor for local unit tests.
- Updated runtime MCP unit tests to expect Phase4 fail-closed behavior instead of Phase3 hard-coded tools.

## Boundary

- `assistant-runtime` still depends only on `assistant-mcp-base` for tool execution contracts.
- No `NoteCommandService`, `notes-data`, DAO, Room, or repository implementation is imported by runtime.
- Tools still fail closed until a real executor is injected in a later Phase4 gate.
