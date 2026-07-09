# Phase4-09 buildfix-01: confirmation toolchain stabilization

## Problem

Voice-triggered high-risk tools such as `notes.delete` could return `requires_confirmation`, but the user-visible confirmation path was still split across several paths:

- The App could display a confirmation dialog only after an explicit `ui.show_confirmation` tool call.
- The dialog confirmation executed directly through the command service, but did not update the global tool-call feedback state.
- `assistant.confirm` and `assistant.reject` required `confirmation_id`, so natural voice replies such as "确认" could fail, then the model could repeatedly call pending-confirmation helper tools.
- The runtime marked every non-success tool response as `blocked`, including `requires_confirmation`, which made diagnostics misleading.

## Fix

This overlay stabilizes the confirmation chain without changing the command execution boundary.

### Runtime

- `McpProtocolClient` still executes tools only through injected `McpToolExecutor`.
- `requires_confirmation` and `partial_success` are no longer marked as `blocked` in `McpProtocolResponse`.
- `failed`, `blocked`, and `not_implemented` remain blocking failures.

### App confirmation UI

- `UiCommandViewModel` now observes `ToolCallEventStore.state`.
- AppNavigation automatically opens a confirmation dialog when the latest tool-call state is `RequiresConfirmation` and contains a `confirmationId`.
- Dialog confirm/reject updates `ToolCallEventStore` through `assistant.confirm` / `assistant.reject`-style tool-call states.
- This means a local button confirmation now visibly transitions from pending to success/failure.

### Assistant confirm/reject tools

- `assistant.confirm` and `assistant.reject` now accept optional `confirmation_id`.
- If `confirmation_id` is omitted and exactly one pending confirmation exists, the tool confirms/rejects that pending operation.
- If zero or multiple pending confirmations exist, the tool fails with a concise user-facing error instead of looping ambiguously.

## Validation focus

1. Trigger `notes.delete` from Fake or Real Xiaozhi.
2. The App should show a confirmation dialog immediately from the tool result, without waiting for `ui.show_confirmation`.
3. Tap confirm.
4. The note should move to recent deleted notes.
5. The tool feedback banner should show `assistant.confirm` success with the command result detail.
6. Say only "确认" after a single pending confirmation.
7. `assistant.confirm {}` should confirm that pending operation.
8. If two pending confirmations exist, `assistant.confirm {}` should ask for `confirmation_id` instead of executing the wrong operation.

## Files

- `assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/mcp/McpProtocolClient.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/assistant/AssistantConfirmTool.kt`
- `assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/assistant/AssistantRejectTool.kt`
- `app/src/main/java/com/er1cmo/noteassistant/UiCommandViewModel.kt`
- `app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt`
