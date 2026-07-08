# Phase3-07 Real Audio / Opus / Playback Report

## Scope

Phase3-07 migrates the real foreground push-to-talk audio path into `assistant-runtime` while preserving the Phase3 safety boundary:

- No wake word.
- No background recording.
- No foreground service microphone runtime.
- No real note-changing MCP tool execution.
- No `NoteCommandService` calls from `assistant-runtime`.

## Implemented

- Added Android audio constants for Xiaozhi-compatible 16 kHz mono 20 ms uplink frames and 48 kHz AudioTrack playback.
- Added `AndroidAudioRecorder` using `AudioRecord` with optional AEC / noise suppressor / AGC discovery.
- Added `AndroidOpusEncoder` using `MediaCodec` `audio/opus` for microphone PCM -> Opus packets.
- Added `AndroidOpusDecoder` using `MediaCodec` `audio/opus` with OpusHead CSD for server/downlink Opus packets.
- Added `AndroidAudioPlayer` using `AudioTrack` streaming PCM16 speech output.
- Added `RealAudioEngine` to coordinate:
  - `AudioRecord -> AndroidOpusEncoder -> WebSocket audio frames`
  - release-to-stop budget check with 300 ms timeout
  - loopback downlink validation through `AndroidOpusDecoder -> AudioTrack`
- Updated `LocalAssistantController` so the existing permission-gated PTT entry now exercises real audio instead of fake audio.

## Acceptance Notes

The current UI still uses the Phase3 settings panel and the existing PTT buttons. The important runtime behavior changed:

- `开始 PTT（请求麦克风）` now starts real `AudioRecord` and Android `MediaCodec` Opus encoding.
- `结束 PTT` stops the recorder, sends `listen/stop`, and attempts a loopback playback validation using encoded Opus frames as simulated server binary audio.
- The path still uses Fake WebSocket for Gate A validation. Real WebSocket service audio compatibility remains part of Gate B.

## Safety Boundary

`assistant-runtime` still owns only assistant runtime state, audio, protocol, and network. It does not import notes data, Room DAO, repository implementation, or `NoteCommandService`.

