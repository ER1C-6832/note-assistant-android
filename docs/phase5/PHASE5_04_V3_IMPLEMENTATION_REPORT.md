# Phase5-04 v3 Implementation Report

Baseline: `f90659eae2d5a7270200b5a6326926c525784f3a` (`tts`)

## Scope

This patch is the system-stability pass after custom wake words and TTS barge-in. It does not redesign KWS, VAD, streaming conversation, MCP tools, or Phase 4 confirmation.

Implemented:

1. Application-scoped Android audio focus coordinator.
2. Clean interruption handling for focus loss, calls/media competition, wired/Bluetooth route removal, and `ACTION_AUDIO_BECOMING_NOISY`.
3. Runtime `RECORD_AUDIO` revocation watchdog and safe recovery after permission restoration.
4. KWS AudioRecord read watchdog with bounded exponential self-recovery.
5. Wake-word detection debounce before the KWS-to-streaming handoff.
6. MCP `tools/call` deduplication by session/conversation/request id.
7. Persistent command-log cap of 2,000 newest records.
8. Regression tests for the bounded MCP request cache.

## System audio behavior

The app does not request `READ_PHONE_STATE`. Incoming calls and competing audio sessions are handled through Android audio focus and `AudioManager.mode`.

When focus or the active route is lost:

```text
stop local capture/playback
-> send abort when a real voice turn is active
-> invalidate streaming/PTT callbacks
-> release AssistantCapture lease
-> clear the active turn context
-> remain paused until system audio is safe
-> restore KWS only after recovery guard
```

A Bluetooth or wired route being added does not automatically restart an interrupted voice session. It only allows the app to return to safe KWS standby. This avoids replaying a stale user turn.

## Runtime permission behavior

While a voice session is active, permission is checked every 500 ms. If `RECORD_AUDIO` is revoked:

```text
abort active voice turn
-> stop recording and playback
-> release microphone owner
-> do not restart KWS without permission
```

After permission is restored and no assistant capture is active, the coordinator resumes KWS.

## KWS recovery

The KWS engine now:

- rechecks microphone permission while running;
- counts consecutive `AudioRecord.read()` failures;
- emits a specific `audio_read_failed` event after repeated failures;
- distinguishes runtime permission revocation from model/JNI errors.

The foreground service automatically retries recoverable errors with 1, 2, 4, 8, 16, then 30 second delays. It does not retry missing permission, invalid custom grammar, missing JNI, or missing model assets.

## Duplicate protection

### Wake word

The same raw wake keyword received again within 2 seconds is ignored before application handoff. Existing atomic handoff protection remains.

### MCP

`tools/call` responses are cached by:

```text
WebSocket session id
+ conversation id
+ JSON-RPC request id
```

A duplicate request in the same session returns the existing response and does not execute a destructive tool twice. The in-memory cache is LRU bounded to 64 entries.

## Log upper limit

`assistant_command_log` keeps the newest 2,000 records. Pruning runs after standalone and transactional command-log insertion. Note revisions and pending confirmations are not deleted by this rule.

## Files added

- `app/.../stability/AssistantSystemAudioCoordinator.kt`
- `assistant-runtime/.../mcp/McpRequestDeduplicator.kt`
- `assistant-runtime/src/test/.../McpRequestDeduplicatorTest.kt`

## Files modified

- `NoteAssistantApp.kt`
- `AssistantController.kt`
- `LocalAssistantController.kt`
- `McpProtocolClient.kt`
- `WakeWordAssistantBridge.kt`
- `SherpaWakeWordEngine.kt`
- `WakeWordForegroundService.kt`
- `AssistantCommandLogDao.kt`
- `CommandTraceRepositoryImpl.kt`
- `.gitignore`

## Deliberate non-goals

- No automatic restart of an interrupted user conversation.
- No notification action that directly confirms a high-risk operation.
- No KWS-based TTS interruption.
- No `READ_PHONE_STATE` permission.
- No database schema migration; only DAO queries and repository pruning are added.
