# Phase3-00 xiaozhi-android Protocol Audit Report

## Conclusion

The updated Phase3 spec direction is correct. The reference `xiaozhi-android` repository already contains usable source material for OTA/activation, persistent device identity, WebSocket handshake, listen/start-stop messages, MCP envelope handling, audio uplink/downlink, and runtime state transitions.

Phase3 should proceed with the current safety boundary: runtime migration only, no note mutation, no `NoteCommandService` import inside `assistant-runtime`.

## Reference audit

| Protocol item | xiaozhi-android reference | Note for migration |
| --- | --- | --- |
| Device identity | `data/identity/DeviceIdentityManager.kt` | Generates `clientId`, pseudo MAC `deviceId`, `serialNumber`, `hmacKey`; persists identity. |
| App config | `data/config/AppConfig.kt`, `ConfigRepository.kt` | Stores OTA URL, authorization URL, activation state, websocket URL/token, identity fields. |
| OTA endpoint | `data/ota/OtaActivationClient.kt` | POSTs OTA payload to `otaUrl`, parses `websocket.url`, `websocket.token`, and optional `activation`. |
| Activation polling | `data/ota/OtaActivationClient.kt` | Uses `/activate`, HMAC-SHA256 over challenge, retries with 202 pending and 200 success. |
| WebSocket request | `network/XiaozhiWebSocketClient.kt` | Headers: `Authorization: Bearer <token>`, `Protocol-Version: 1`, `Device-Id`, `Client-Id`. |
| Client hello | `network/XiaozhiMessage.kt` | Sends `type=hello`, `version=1`, `features.mcp=true`, `transport=websocket`, Opus audio params. |
| Listen detect text | `network/XiaozhiMessage.kt` | Sends `type=listen`, `state=detect`, `text=<input>`. This is the current text conversation entry. |
| Listen start | `network/XiaozhiMessage.kt` | Sends `type=listen`, `state=start`, `mode=manual/realtime`. |
| Listen stop | `network/XiaozhiMessage.kt` | Sends `type=listen`, `state=stop`. |
| Abort | `network/XiaozhiMessage.kt` | Sends `type=abort`, default reason `user_interruption`. |
| MCP envelope | `network/XiaozhiMessage.kt` | Sends `type=mcp`, `payload=<object>`. Phase3 blocks note mutations. |
| Incoming JSON | `network/XiaozhiWebSocketClient.kt`, `protocol/ProtocolEvent.kt` | Parses message type, session id, and emits JSON protocol events. |
| Incoming binary audio | `network/XiaozhiWebSocketClient.kt`, `audio/AudioEngine.kt` | Binary WebSocket frames are audio frames and enter playback queue. |
| Audio uplink | `audio/AudioEngine.kt` | AudioRecord -> PCM16 -> OpusEncoder -> WebSocket binary frame. 16 kHz mono 20 ms input frame. |
| Audio downlink | `audio/AudioEngine.kt` | WebSocket binary frame -> OpusDecoder -> PCM -> AudioPlayer/AudioTrack. |

## Key findings

1. The real protocol should not be implemented from memory. `xiaozhi-android` provides concrete message and header shapes.
2. Text conversation in the reference currently uses `listen/detect` with text, not a separate generic `chat.text` envelope.
3. Audio is the highest-risk migration area. The reference uses 16 kHz mono 20 ms input, Opus encoder, and binary WebSocket frames.
4. MCP should be moved first as protocol shape only. Phase3 must return blocked/not implemented for notes/tags mutations.
5. Wake word and realtime background listening are visible in the reference `MainViewModel`, but remain out of Phase3 scope for this app.

## Phase3 sub-stage recommendation

- Phase3-01: assistant-runtime skeleton and fake runtime state machine.
- Phase3-02: settings UI grouping and runtime controls.
- Phase3-03: device identity and fake/real activation data model.
- Phase3-04: fake WebSocket and text conversation gate A.
- Phase3-05: real WebSocket handshake and message routing gate B start.
- Phase3-06: MCP protocol blocked boundary.
- Phase3-07: push-to-talk permission and fake audio.
- Phase3-08: real Opus/audio compatibility.
