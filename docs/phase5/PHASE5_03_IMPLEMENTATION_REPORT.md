# Phase5-03 Implementation Report

## Baseline

- Repository: `ER1C-6832/note-assistant-android`
- Expected base commit: `436ccfb9bc2c54069561f028600a27aa630e2999`
- Phase5-01 KWS acceptance: passed by user
- Phase5-02 dual-mode and streaming acceptance: passed by user

## Implemented chain

```text
WakeWordForegroundService
    -> WakeWordCoordinator.detections
        -> application-scoped WakeWordAssistantBridge
            -> activation / connection preparation
                -> local acknowledgement tone
                    -> AssistantController.startStreamingConversation(
                           source = WakeWord
                       )
                        -> existing Real Runtime
                            -> existing MCP tools
                                -> existing NoteCommandService
```

## Source propagation

A new singleton `AssistantTurnContextStore` is shared by `LocalAssistantController` and `XiaozhiWebSocketClient`.

Wake-started streaming sessions store:

```text
entrySource = WakeWord
conversationId = streamingSessionId
wakeKeyword = detected phrase
```

Every incoming real `tools/call` obtains a fresh `McpToolContext` from this store:

```text
WakeWord -> McpToolContext.SOURCE_WAKEWORD
PushToTalk / StreamingButton / Text -> McpToolContext.SOURCE_VOICE
```

The existing assistant-tools mapper already converts `SOURCE_WAKEWORD` to `CommandSource.Wakeword`; no note command or MCP tool was copied or bypassed.

The context remains active for every turn in the same streaming session and is cleared when the session ends. Starting a later PTT session overwrites it with ordinary voice source.

## Activation and connection behavior

On KWS detection the bridge:

1. rejects duplicate concurrent handoffs;
2. verifies microphone permission;
3. enables the assistant when needed;
4. switches to Real Runtime when needed;
5. prepares device identity;
6. runs the existing Real OTA/activation path when not already activated;
7. connects the existing Real WebSocket;
8. plays a short non-speech acknowledgement tone;
9. starts streaming conversation with WakeWord source.

If activation or connection fails, assistant capture is not opened and KWS is resumed.

## Acknowledgement tone

The tone is played after KWS has emitted `Detected`. In the migrated engine, `Detected` is emitted after its AudioRecord cleanup. The assistant streaming capture is started only after the tone and a short guard interval complete, preventing the tone from being uploaded as user audio.

## Confirmation behavior

High-risk commands are unchanged:

```text
MCP tool -> requires_confirmation -> persisted pending confirmation
```

A spoken `确认` or `取消` remains in the same wake-started streaming session and therefore carries WakeWord source through existing `assistant.confirm` / `assistant.reject` tools.

When a new pending confirmation is observed in a wake-started session, the existing foreground notification changes to `有待确认的便签操作，点击打开应用`. The notification opens the app. It intentionally does not include a direct confirm action.

## KWS and TTS

KWS has already stopped after detection and remains paused during the entire streaming session. It is resumed only by the existing session-stop/failure path. This prevents assistant TTS from producing a second KWS session.

## Files added

```text
assistant-runtime/.../context/AssistantTurnContextStore.kt
assistant-runtime/.../context/AssistantTurnContextStoreTest.kt
app/.../wakeword/WakeWordAssistantBridge.kt
```

## Files patched

```text
app/.../NoteAssistantApp.kt
assistant-runtime/.../controller/LocalAssistantController.kt
assistant-runtime/.../network/XiaozhiWebSocketClient.kt
assistant-wakeword/.../WakeWordCoordinator.kt
assistant-wakeword/.../WakeWordForegroundService.kt
assistant-wakeword/.../WakeWordServiceController.kt
.gitignore
```

## Validation performed before packaging

- Python patch script syntax check.
- POSIX shell syntax check.
- First-application simulation against baseline-shaped source markers.
- Repeat-application/idempotency simulation.
- New acknowledgement bridge Kotlin syntax compile using Android/project stubs plus real coroutines library.
- New context-store Kotlin syntax compile using project stubs.
- Boundary scan: bridge contains no `NoteCommandService`, DAO, Room, or MCP executor dependency.
- ZIP content and SHA-256 verification.

A complete Android Gradle/Hilt build and real-device timing verification must run on the user's workstation.
