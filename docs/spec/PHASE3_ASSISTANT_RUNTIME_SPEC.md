# Phase 3 Assistant Runtime Specification

> Status: Draft specification for Phase 3.
> Scope: Assistant runtime main path: activation, WebSocket connection, protocol routing, text conversation, push-to-talk audio, TTS playback, runtime state, settings, and testability.
> Phase 3 must not execute real note-changing tools. Notes MCP tool execution belongs to Phase 4.

## 1. Purpose

Phase 3 migrates and adapts the voice assistant runtime from `xiaozhi-android` into this notes-first app.

Phase 1 made the app a usable manual notes product. Phase 2 made command execution trustworthy and traceable. Phase 3 answers a different question:

```text
Can the app connect to the Xiaozhi assistant service, exchange messages, capture voice, play responses, recover from runtime errors, and expose clear assistant state without modifying notes?
```

The output of Phase 3 is a working assistant runtime that can hold a text and voice conversation, but note operations remain disabled, mocked, or read-only until Phase 4.

## 2. Product Contract

From the user's point of view, Phase 3 must satisfy these promises:

- The assistant can be enabled from settings without breaking the manual notes app.
- The app clearly shows whether the assistant is idle, connecting, connected, listening, thinking, speaking, or in error.
- The user can run a text conversation test.
- The user can press and hold to speak after granting microphone permission.
- The user can hear assistant audio replies when the server sends playable audio or TTS output.
- Network, activation, and microphone failures are shown clearly and do not destroy local notes.
- Turning off the assistant stops active runtime work.

From the developer's point of view, Phase 3 must satisfy these constraints:

- `assistant-runtime` owns assistant state, protocol, network, audio, and activation runtime logic.
- `assistant-runtime` must not depend on `notes-data`, Room DAOs, or note repository implementations.
- `assistant-runtime` may depend on `assistant-mcp-base`, `app-settings`, and `core-common`.
- Runtime events that would become MCP tool calls must stop at a mock/read-only boundary in Phase 3.
- Phase 3 may list tools and route protocol messages, but must not invoke `NoteCommandService` for note mutations.
- Wake word and foreground background listening remain out of scope until Phase 5.

## 3. Relationship to Earlier Phases

Phase 1 owns:

- Manual notes UI and note CRUD behavior.
- Search, tags, pin, archive, recently deleted, and theme polish.

Phase 2 owns:

- Trusted command path.
- Risk policy.
- Pending confirmations.
- Command logs.
- Revisions.
- Local tool simulator.

Phase 3 owns:

- Device identity.
- OTA or activation flow.
- WebSocket lifecycle.
- Xiaozhi message protocol.
- Conversation state machine.
- Text conversation.
- Push-to-talk audio capture.
- Audio playback.
- Opus encode/decode adapter boundary.
- Assistant settings and status UI.
- Runtime test doubles.

Phase 4 will connect assistant MCP tool calls to Phase 2 `NoteCommandService`.

## 4. Included

- `AssistantController` production state machine and public runtime API.
- `AssistantState` with stable phase, connection, activation, audio, and error fields.
- `DeviceIdentityManager`.
- `OtaActivationClient`.
- `XiaozhiWebSocketClient`.
- `XiaozhiMessageBuilder`.
- `XiaozhiMessageRouter`.
- `ProtocolEvent`.
- `McpProtocolClient` transport-level handling only.
- `ConversationStateMachine`.
- `ConversationSession`.
- `AudioEngine`.
- `AudioRecorder`.
- `AudioPlayer` or `TtsPlayer`.
- `OpusEncoder` and `OpusDecoder` interfaces or adapters.
- Assistant settings storage for endpoint, activation data, device identity, and runtime toggles.
- Settings/debug UI for runtime status and text conversation test.
- A clearly separated settings/debug information architecture for Phase 2 and Phase 3 tools.
- Protocol compatibility checklist that traces each migrated protocol item back to the `xiaozhi-android` reference source.
- Two acceptance gates: fake runtime acceptance and real Xiaozhi path acceptance.
- Permission flow for foreground push-to-talk microphone use.
- Reconnect and error recovery policy.
- Unit tests for state machine, message routing, activation parsing, and reconnect decisions.
- Integration tests with fake WebSocket or MockWebServer where practical.

## 5. Excluded

- Real note-changing MCP tool execution.
- Voice-created notes.
- Voice search against real notes, except optional read-only echo/mock.
- Wake word detection.
- Background listening.
- Foreground service microphone runtime.
- sherpa-onnx KWS.
- Notification permission flow for wake word service.
- Cloud sync.
- Any bypass of Phase 2 `NoteCommandService`.
- General Android system assistant tools from `xiaozhi-android`.

## 6. Terms

- `Assistant runtime`: The set of components that manage activation, WebSocket, protocol, audio, and assistant state.
- `Activation`: The process that obtains or validates server-side assistant/device credentials.
- `Device identity`: Stable local identity used by the assistant service.
- `Session`: One active connection/conversation lifecycle.
- `Text conversation`: A user sends typed text and receives assistant text/audio response.
- `Push-to-talk`: User holds a UI control to stream microphone audio, then releases to stop.
- `Protocol event`: A typed event parsed from incoming Xiaozhi protocol messages.
- `Tool call`: A protocol request to call an MCP tool. In Phase 3 this is mocked, blocked, or read-only.

## 7. Global Rules

### 7.1 Runtime Boundary

- `AssistantController` is the public entry point for UI.
- UI must not directly call WebSocket, audio recorder, audio player, or activation classes.
- `AssistantController` exposes `StateFlow<AssistantState>`.
- Runtime side effects must be cancellable through controller lifecycle methods.
- Runtime must continue to compile and run when notes modules are unavailable, except for app-level DI wiring.

### 7.2 Note Safety

- Phase 3 must not mutate notes through voice, MCP, or protocol messages.
- Incoming MCP tool calls for note-changing tools must return a safe blocked/not-implemented result.
- If a server requests a note tool during Phase 3, the app must log/debug-display the request but not execute it.
- Read-only tool listing is allowed.
- The first phase that may execute real notes tools is Phase 4, and it must route through Phase 2 command safety.

### 7.3 Permissions

- App startup must not request microphone permission.
- Opening assistant settings must not request microphone permission.
- Text conversation must not request microphone permission.
- Push-to-talk requests `RECORD_AUDIO` only when the user initiates voice input.
- If permission is denied, assistant state becomes an error or blocked state with a clear message, and no recording starts.
- Phase 3 must not request notification permission for wake word/background behavior.

### 7.4 State Model

`AssistantState` must represent at least:

```text
phase:
- idle
- disabled
- activating
- connecting
- connected
- listening
- uploading_audio
- thinking
- speaking
- reconnecting
- error

connection:
- disconnected
- connecting
- connected
- closing

activation:
- unknown
- required
- activating
- activated
- failed

audio:
- idle
- recording
- playing
- error
```

Additional fields:

```text
statusText: String
errorMessage: String?
lastUserText: String?
lastAssistantText: String?
lastEventAt: Long?
sessionId: String?
reconnectAttempt: Int
```

Rules:

- Default state must not be `connected` unless a real connection exists.
- State transitions must be deterministic and testable.
- Errors must preserve enough state for UI to explain what failed.

### 7.5 Settings

Phase 3 settings must include:

- Assistant enabled toggle.
- WebSocket URL.
- OTA/activation endpoint or activation mode if required by the migrated runtime.
- Device identity display/reset debug action.
- Connection status.
- Activation status.
- Text conversation test input.
- Push-to-talk test entry.
- Last error display.

Settings structure rules:

- Phase 3 runtime controls must not be appended as an undifferentiated list below existing Phase 2 debug tools.
- Settings must be grouped into clear sections:
  - Notes settings.
  - Phase 2 command/debug tools.
  - Phase 3 assistant runtime.
  - Developer debug, collapsible if the UI becomes dense.
- Long command logs, revision debug panels, protocol logs, and runtime diagnostics should live in a developer/debug section rather than the ordinary user settings surface.
- A normal user who only wants notes and assistant enable/disable should not need to scan simulator, revision, or protocol internals.

Sensitive values:

- Device identifiers may be stored locally.
- Tokens or activation secrets must not be printed in normal UI.
- Debug logs may mask secrets.

## 8. Acceptance Gates

Phase 3 has two gates. Gate A is required before runtime implementation can be considered internally usable. Gate B is required before Phase 3 can be called complete for real assistant integration.

### 8.1 Gate A: Fake Runtime Acceptance

Gate A verifies the app architecture and UI behavior without depending on the real Xiaozhi service.

Required:

- Fake activation succeeds and fails deterministically.
- Fake WebSocket can connect, receive a text request, and return a fake assistant text response.
- Fake audio path can accept push-to-talk lifecycle events.
- Fake audio frame or fake TTS playback path can drive `speaking` state.
- Fake tool-call message for a note mutation returns blocked/not implemented.
- Runtime state transitions are visible in settings/app UI.
- No note data is mutated.
- Tests cover state machine, fake protocol routing, fake text conversation, fake audio lifecycle, and blocked tool call.

Gate A completion does not prove compatibility with the real Xiaozhi service.

### 8.2 Gate B: Real Xiaozhi Path Acceptance

Gate B verifies compatibility with the actual target assistant service or a Xiaozhi-compatible endpoint.

Required:

- Real OTA/activation works or the documented real activation path is verified.
- Real WebSocket handshake succeeds.
- Real text message request receives an assistant response.
- Real audio input uses the correct sample rate, channels, frame size, timing, and encoding expected by the service.
- Real server audio/TTS response can be decoded and played.
- Real server tool-call message is parsed and blocked safely in Phase 3.
- Reconnect behavior is tested against real close/error conditions where practical.
- Protocol compatibility checklist is updated with real verification status.

If Gate B cannot be completed because service credentials or endpoint access are unavailable, Phase 3 may be marked "Gate A complete, Gate B blocked" but not fully complete.

## 9. Protocol Compatibility Checklist

Phase 3 migration must maintain a protocol checklist. The checklist may live in this spec while small, or in a Phase3-00 audit report if it becomes large.

Each row must identify:

```text
Protocol item
Reference file/function in xiaozhi-android
Implemented file/function in note-assistant-android
Fake verified: yes/no
Real verified: yes/no
Notes / gaps
```

Minimum checklist:

| Protocol item | Reference | Implementation | Fake verified | Real verified | Notes |
| --- | --- | --- | --- | --- | --- |
| Activation endpoint | TBD from `xiaozhi-android` | TBD | no | no | |
| WebSocket URL | TBD from `xiaozhi-android` | `SettingsRepository` / runtime config | no | no | |
| WebSocket headers | TBD from `xiaozhi-android` | TBD | no | no | |
| Device id field | TBD from `xiaozhi-android` | `DeviceIdentityManager` | no | no | |
| Client hello / session start | TBD from `xiaozhi-android` | `XiaozhiMessageBuilder` | no | no | |
| Listen start message | TBD from `xiaozhi-android` | `XiaozhiMessageBuilder` | no | no | |
| Listen stop message | TBD from `xiaozhi-android` | `XiaozhiMessageBuilder` | no | no | |
| Text input message | TBD from `xiaozhi-android` | `XiaozhiMessageBuilder` | no | no | |
| Audio frame message | TBD from `xiaozhi-android` | `AudioEngine` / `XiaozhiWebSocketClient` | no | no | |
| Server text message | TBD from `xiaozhi-android` | `XiaozhiMessageRouter` | no | no | |
| Server audio message | TBD from `xiaozhi-android` | `XiaozhiMessageRouter` / `AudioEngine` | no | no | |
| Server tool-call message | TBD from `xiaozhi-android` | `McpProtocolClient` | no | no | Must block note mutations in Phase 3. |
| `tools/list` response | TBD from `xiaozhi-android` | `McpProtocolClient` | no | no | |
| `tools/call` blocked response | TBD from `xiaozhi-android` | `McpProtocolClient` | no | no | |

Rules:

- Do not implement protocol fields from memory when a `xiaozhi-android` reference exists.
- Phase3-00 should audit the source files and fill the reference column before protocol-heavy implementation begins.
- Any intentional deviation from `xiaozhi-android` must be documented.
- Gate B cannot pass while required real-verified fields remain unknown.

## 10. Runtime Components

### 10.1 `DeviceIdentityManager`

Responsibilities:

- Generate or load stable device id.
- Persist device identity.
- Provide identity to activation and WebSocket handshake.
- Support debug reset only from settings/debug UI.

Acceptance:

- Given identity exists, When app restarts, Then the same identity is reused.
- Given identity is reset, When runtime starts again, Then a new identity is generated.

### 10.2 `OtaActivationClient`

Responsibilities:

- Perform activation request using endpoint and device identity.
- Parse success, pending, already activated, and failure responses.
- Store activation result needed for WebSocket.
- Surface user-safe error messages.

Acceptance:

- Given activation succeeds, Then state becomes `activated`.
- Given activation fails due to network, Then state becomes `error` with retry available.
- Given existing valid activation, Then runtime does not reactivate unnecessarily.

### 10.3 `XiaozhiWebSocketClient`

Responsibilities:

- Connect to configured WebSocket URL.
- Send handshake/session start message.
- Send text messages.
- Send encoded audio frames.
- Receive text, audio, control, and tool-call messages.
- Emit connection and message events.
- Close cleanly.

Rules:

- No UI class may hold the WebSocket instance.
- Connection close must cancel active send/receive work.
- Incoming malformed messages become protocol error events, not crashes.

Acceptance:

- Given a valid fake server, When connect is called, Then state reaches `connected`.
- Given server closes connection, Then state becomes `disconnected` or `reconnecting` according to policy.

### 10.4 `XiaozhiMessageRouter`

Responsibilities:

- Parse raw incoming messages.
- Convert messages into typed `ProtocolEvent`.
- Route assistant text/audio/control events to `ConversationStateMachine`.
- Route tool-call messages to `McpProtocolClient` mock/block boundary in Phase 3.

Acceptance:

- Given assistant text message, Then `lastAssistantText` updates.
- Given tool call message for `notes.delete`, Then no note mutation happens and a blocked result is returned.

### 10.5 `ConversationStateMachine`

Responsibilities:

- Own legal conversation state transitions.
- Distinguish text input, audio input, thinking, speaking, interruption, and errors.
- Prevent invalid transitions such as recording while disabled.

Acceptance:

- Given idle connected runtime, When text is sent, Then state moves through `thinking` and can return to `connected`.
- Given user starts push-to-talk, Then state becomes `listening` or `uploading_audio`.
- Given user releases push-to-talk, Then recorder stops and state becomes `thinking` or `connected`.

### 10.6 `AudioEngine`

Responsibilities:

- Coordinate recorder, encoder, decoder, and player.
- Start microphone capture for push-to-talk.
- Stop microphone capture on release/cancel/error.
- Stream encoded frames to WebSocket.
- Play decoded assistant audio frames or TTS output.

Rules:

- Microphone is used only during explicit push-to-talk in Phase 3.
- Recording must stop when app loses permission, runtime disables, connection closes, or user releases button.
- Audio errors must not crash the app.

Acceptance:

- Given microphone permission is granted, When user holds push-to-talk, Then recorder starts.
- Given user releases, Then recorder stops within 300ms.
- Given playback frame arrives, Then player receives decoded audio.

### 10.7 `OpusEncoder` and `OpusDecoder`

Responsibilities:

- Provide stable interfaces around migrated Opus implementation.
- Hide platform/MediaCodec/native details from `AssistantController`.
- Support fake implementations for tests.

Rules:

- If real Opus is not available in early Phase 3, interfaces and fake passthrough implementations may be used only behind a clearly documented adapter.
- Phase 3 completion requires real or migrated encoding/decoding sufficient for the target Xiaozhi service test path.
- Opus work must be tracked as its own Phase 3 sub-stage because it is the highest-risk part of the runtime path.
- The Opus sub-stage must document sample rate, channel count, PCM format, frame duration, bytes per frame, packet timing, and expected server payload shape.
- "Recorder starts" is not enough to pass audio acceptance. The service must be able to understand the encoded stream for Gate B.
- "Audio frame received" is not enough to pass playback acceptance. The app must decode and play the server response for Gate B.

Recommended Opus sub-stage:

```text
Phase3-Audio-01: interfaces and fake passthrough
Phase3-Audio-02: real recorder/player lifecycle
Phase3-Audio-03: migrated Opus encode/decode
Phase3-Audio-04: real service audio compatibility test
```

### 10.8 `McpProtocolClient`

Responsibilities in Phase 3:

- Understand tools/list and tools/call protocol shape.
- Return tool list if needed.
- Block or mock note-changing tool calls.
- Never call `NoteCommandService` in Phase 3.

Acceptance:

- Given server requests tools/list, Then app returns available Phase 3-safe tool metadata.
- Given server requests notes mutation tool, Then app returns `blocked` or `not_implemented` and command logs are not written as real mutations.

## 11. User Flows

### FLOW-01 Enable Assistant

Normal flow:

1. User opens Settings.
2. User enables assistant.
3. App loads device identity.
4. App checks activation status.
5. App shows idle or connected-ready state.

Negative paths:

- Invalid URL blocks connection and shows validation error.
- Activation failure shows retry.

### FLOW-02 Text Conversation Test

Normal flow:

1. User enters text in assistant debug/test field.
2. User taps send.
3. Runtime ensures activated/connected state.
4. Runtime sends text message over WebSocket.
5. Incoming assistant response updates state and visible response text.

Acceptance:

- Text conversation does not request microphone permission.
- Sending while disconnected triggers connect or returns clear error.

### FLOW-03 Push-to-Talk

Normal flow:

1. User presses and holds voice control.
2. App requests microphone permission if not already granted.
3. Runtime connects if needed.
4. Audio recording starts.
5. Audio frames are encoded and sent.
6. User releases.
7. Recording stops.
8. Runtime waits for assistant response.
9. Assistant audio/text response is played/displayed.

Negative paths:

- Permission denied: no recording starts.
- Connection fails: no recording starts or recording stops with error.
- Encoder fails: recording stops and error is shown.

### FLOW-04 Runtime Disable

Normal flow:

1. User disables assistant.
2. Runtime stops recording/playback.
3. WebSocket closes.
4. Pending runtime jobs are cancelled.
5. State becomes `disabled`.

Acceptance:

- Disabling assistant never modifies notes.
- Disabling assistant clears active audio resources.

## 12. Error and Recovery Policy

| Case | Required behavior |
| --- | --- |
| Invalid WebSocket URL | Do not connect; show validation error. |
| Activation network failure | Show retryable activation error. |
| WebSocket connect timeout | Move to error or reconnecting with attempt count. |
| Server close normal | Move to disconnected. |
| Server close abnormal | Reconnect according to policy. |
| Malformed protocol message | Emit protocol error; keep connection if possible. |
| Microphone permission denied | Stop voice flow and show permission message. |
| AudioRecord start fails | Stop voice flow and show audio error. |
| Audio playback fails | Continue conversation if possible and show playback error. |
| Tool call for notes mutation | Return blocked/not implemented; do not mutate notes. |

Reconnect policy:

- Use bounded exponential backoff or fixed retry with max attempts.
- Reconnect only when assistant is enabled.
- Do not reconnect while user has explicitly disabled assistant.
- UI must show reconnect attempt count or clear reconnecting status.

## 13. Testing Contract

Minimum tests:

- `AssistantState` default is idle/disabled, not connected.
- `ConversationStateMachine` accepts legal transitions and rejects invalid ones.
- `DeviceIdentityManager` persists identity.
- `OtaActivationClient` parses success and failure responses.
- `XiaozhiMessageRouter` parses text/audio/control/tool-call messages.
- Tool-call routing blocks note mutation tools in Phase 3.
- WebSocket client connects to fake server and emits connected/disconnected events.
- Reconnect policy stops after max attempts.
- Text conversation sends expected message shape.
- Push-to-talk starts/stops fake recorder on press/release.
- Permission denied prevents recording.
- Audio playback errors move state to error without crashing.
- Gate A fake runtime test verifies fake activation, fake WebSocket, fake text response, fake audio lifecycle, fake playback state, and blocked note tool call.
- Protocol compatibility checklist has tests or manual verification notes for each fake-verified item.
- Opus sub-stage tests verify frame sizing and encode/decode adapter behavior.

Recommended tools:

- Fake WebSocket server or MockWebServer.
- Fake `AudioRecorder`.
- Fake `AudioPlayer`.
- Fake `OpusEncoder`.
- Fake `OpusDecoder`.
- Turbine for `StateFlow` assertions.

## 14. Architecture Rules

- `assistant-runtime` must not import Room, DAO, `notes-data`, or `NoteRepositoryImpl`.
- `assistant-runtime` must not import `NoteCommandService` in Phase 3.
- `assistant-tools` may remain not implemented for note mutation tools until Phase 4.
- `app` wires runtime dependencies through Hilt.
- Settings UI talks to `AssistantController` and settings repositories, not low-level runtime classes.
- Protocol models should be strongly typed before reaching controller state.
- Settings UI must keep Phase 2 command debugging and Phase 3 runtime debugging visually separated.

## 15. Phase Completion Definition

Phase 3 is complete when:

- Gate A fake runtime acceptance passes.
- Gate B real Xiaozhi path acceptance passes, or Phase 3 is explicitly marked "Gate A complete, Gate B blocked" with the blocking reason documented.
- Assistant can be enabled/disabled from settings.
- Device identity is persisted.
- OTA/activation path works for real Xiaozhi service, or the real blocker is documented separately from fake activation success.
- WebSocket can connect to a configured endpoint.
- Text conversation test works against both fake runtime and the real target path when available.
- Push-to-talk records, streams, stops, and handles permission denial.
- Assistant audio or TTS playback path works with fake frames for Gate A and real compatible frames for Gate B.
- Runtime state is visible in settings and app UI.
- Disconnect, reconnect, and error states are handled without app crash.
- Incoming note mutation tool calls are blocked or mocked.
- No Phase 3 path mutates notes.
- Protocol compatibility checklist is filled and kept current.
- Opus/audio compatibility is tracked as a separate risk item and verified before real completion.
- Settings/debug UI is grouped so normal settings, Phase 2 debug, and Phase 3 runtime debug are not mixed into one long undifferentiated panel.
- The app remains fully usable as a manual notes app while assistant is disabled or failing.

## 16. Traceability

This spec refines the original Phase 3 section of:

- `docs/DEVELOPMENT_PLAN.md`

It builds on:

- `docs/spec/PHASE1_NOTES_SPEC.md`
- `docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md`
- `docs/phase2/PHASE2-05-06-07-simulator-revision-test-report.md`

When Phase 3 behavior changes, update this spec in the same commit as the implementation or before implementation begins.
