# Phase5-03 Voice Polish Report

## Scope

This patch addresses post-acceptance voice and UI defects without changing the Phase5 architecture.

## Root causes

### Streaming ended too easily

`VoiceActivityConfig.trailingSilenceMs` was 900 ms. Natural short pauses could end the current turn. The default is now 1200 ms.

### Empty turns entered Thinking

Audio packets were uploaded immediately after `listen/start`, even before local VAD had confirmed speech. PTT always sent `listen/stop`, and streaming stop paths could also finalize an empty turn. The server then had an empty audio turn while the client waited for a response.

The patch now:

- enables non-auto-stopping activity monitoring for PTT;
- records `speechSeen` in `RealAudioStopResult`;
- buffers up to 15 Opus packets (about 300 ms) before first speech;
- uploads the pre-roll only after speech is detected;
- discards the pre-roll if no speech occurs;
- sends protocol `abort` for empty or cancelled turns;
- returns UI state to Connected/Idle instead of Thinking.

### PTT sometimes stayed in Speaking

The client depended on a server TTS stop/end event. If the event was delayed or absent after binary audio finished, the audio state remained Playing and the phase remained Speaking.

The patch adds a 1500 ms downlink-idle fallback. Each binary audio frame refreshes it. On expiry, playback resources are released and the state returns to Connected/Idle. Explicit TTS stop/end still takes priority.

### Feedback card required manual hiding

Terminal feedback cards now auto-clear after 3 seconds. Running and confirmation cards remain visible because hiding them automatically can obscure an active or safety-relevant operation. Any visible card supports left or right swipe dismissal, and the existing Hide button remains.

### Fake controls were visible in the product overlay

The main overlay no longer exposes a runtime toggle. Product actions force Real Runtime and the default `AssistantState` is Real. Fake APIs remain for test/debug compatibility only.

## Modified files

```text
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/audio/VoiceActivityDetector.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/audio/RealAudioEngine.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/network/XiaozhiWebSocketClient.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/state/AssistantState.kt
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/controller/LocalAssistantController.kt
app/src/main/java/com/er1cmo/noteassistant/assistantui/AssistantEntryOverlay.kt
.gitignore
```

## Preserved boundaries

- KWS detection and foreground-service ownership remain unchanged.
- WakeWord-to-streaming bridge remains application-scoped.
- `source=wakeword` session propagation remains unchanged.
- MCP tools, `NoteCommandService`, Room, command logs, revisions, and pending confirmations are unchanged.
- Fake Runtime is not deleted from debug/test code in this patch.
