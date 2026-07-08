# Phase3 GateB Real Xiaozhi Path Report

## Status

This overlay implements the GateB runtime path in code. GateB is only accepted after manual verification against a real Xiaozhi or Xiaozhi-compatible endpoint.

## Implemented

- Runtime mode switch: Fake Runtime / Real Xiaozhi Runtime.
- `LocalAssistantController` routes `connect`, `sendText`, `startPushToTalk`, and `stopPushToTalk` through the selected mode.
- Real mode uses `XiaozhiWebSocketClient` instead of `FakeXiaozhiWebSocketClient`.
- Real WebSocket hello/session handshake updates `gateBRealHandshakeVerified`.
- Real assistant text response updates `gateBRealTextVerified`.
- Real push-to-talk sends `listen/start`, streams Opus frames to the real WebSocket, and sends `listen/stop`.
- Real server binary audio is decoded by `RealAudioEngine` and written to `AudioTrack`; successful playback updates `gateBRealAudioPlaybackVerified`.
- Real server MCP `tools/list` / `tools/call` requests are routed through `McpProtocolClient`; note/tag tools stay blocked in Phase3 and the response is sent back over the real WebSocket.
- GateB diagnostic fields are visible on the settings page.

## Still requires manual verification

- Real OTA / activation against the target service.
- Real WebSocket handshake.
- Real text response.
- Real audio upload being understood by the service.
- Real TTS/audio binary response decoded and played.
- Real server note-mutation tool-call blocked and response sent.

## Safety boundary

Phase3 still does not call `NoteCommandService` from `assistant-runtime`. Real notes MCP execution remains Phase4 work.
