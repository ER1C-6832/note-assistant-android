# Phase3-08 Reconnect / Error / Tests Report

## Scope

Phase3-08 closes the assistant runtime Gate A reliability work:

- Reconnect / abnormal close handling.
- Clear error states for transport failure, permission denial, and audio failure.
- Deterministic state machine transitions.
- Protocol router tests.
- Fake WebSocket tests.
- No note mutation, no `NoteCommandService` call from `assistant-runtime`.

## Implemented

### Runtime recovery

Added `ReconnectPolicy` in `assistant-runtime`:

- Normal close `1000` does not reconnect.
- Abnormal close can reconnect while assistant is enabled.
- Disabled assistant never reconnects.
- Reconnect attempts are capped.

`AssistantState` now records debug-safe recovery metadata:

- `lastCloseCode`
- `lastCloseReason`
- `lastReconnectDecision`
- `runtimeErrorCount`

### Controller boundary

`AssistantController` gained explicit debug/recovery entry points:

- `reconnect()`
- `simulateConnectionClosed(...)`
- `simulateConnectionFailure(...)`
- `simulateAudioFailure(...)`

UI still talks only to `AssistantController`. UI does not hold WebSocket, recorder, encoder, decoder, or player instances.

### Fake WebSocket failure simulation

`FakeXiaozhiWebSocketClient` now supports:

- `simulateServerClose(code, reason)`
- `simulateTransportFailure(message)`

Both clear fake connection state and let the controller surface the recovery decision.

### Settings debug controls

The Phase3 runtime panel now includes:

- Manual reconnect.
- Simulate abnormal close.
- Simulate connection failure.
- Simulate audio failure.

These are debug controls for Gate A validation only.

## Tests

Added/updated:

- `ConversationStateMachineTest`
- `ReconnectPolicyTest`
- `XiaozhiMessageRouterTest`
- `FakeXiaozhiWebSocketClientTest`

Coverage includes:

- Disabled runtime cannot connect.
- PTT enters listening and returns to thinking.
- Reconnecting state is explicit.
- Normal close does not reconnect.
- Abnormal close reconnects when enabled.
- Reconnect attempts are capped.
- Malformed JSON becomes `ProtocolError`.
- `tools/list` returns safe Phase3 tools.
- `notes.delete` tool call is blocked.
- Fake WebSocket connect/text/PTT/MCP/close/failure paths.

## Safety boundary

Phase3-08 does not:

- Execute note-changing tools.
- Call `NoteCommandService` from `assistant-runtime`.
- Import Room / DAO / notes-data from `assistant-runtime`.
- Add wake word, background listening, notification permission, or foreground microphone service.

## Recommended verification

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```

Manual Gate A checks:

1. Enable assistant.
2. Fake activation.
3. Connect Fake.
4. Send text.
5. Start/stop PTT after granting microphone.
6. Simulate abnormal close and verify reconnect/status metadata.
7. Simulate connection failure and verify error message.
8. Simulate audio failure and verify audio error state.
9. Simulate `notes.delete` and verify it is blocked.
10. Confirm notes remain unchanged.
