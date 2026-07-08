# Phase3-06 Push-to-talk Permission + Fake Audio Report

## Scope

Phase3-06 implements the push-to-talk permission boundary and fake audio lifecycle for Gate A validation.

It does not migrate real Android `AudioRecord`, real Opus, real playback, wake word, foreground service, background listening, or note-changing MCP execution.

## Completed

- Added `RECORD_AUDIO` declaration in the app manifest.
- Settings UI now requests `RECORD_AUDIO` only when the user taps the Phase3 PTT start button.
- Text conversation and assistant settings do not request microphone permission.
- Added `FakeAudioEngine` and `FakeOpusEncoder`.
- `LocalAssistantController.startPushToTalk()` now:
  - requires a permission result from UI,
  - connects Fake WebSocket when needed,
  - sends fake `listen/start`,
  - starts fake recorder/encoder state.
- `LocalAssistantController.stopPushToTalk()` now:
  - stops fake recording with a 300 ms budget,
  - uploads fake encoded frames to Fake WebSocket,
  - sends fake `listen/stop`,
  - drives thinking/speaking/connected state transitions.
- Settings UI displays fake audio frame counts and release-to-stop latency.
- Added tests for fake recorder/encoder stop latency and Fake WebSocket audio lifecycle.

## Safety Boundary

- Phase3 still does not call `NoteCommandService` from `assistant-runtime`.
- Phase3 still blocks `notes.*` and `tags.*` MCP mutations.
- No note data is created, updated, deleted, archived, or restored by this stage.
- This stage does not request notification or foreground-service microphone permissions.

## Acceptance

Required checks:

1. App startup does not show a microphone permission prompt.
2. Opening Settings does not show a microphone permission prompt.
3. Text conversation test does not request microphone permission.
4. Pressing Phase3 PTT start triggers `RECORD_AUDIO` only at that moment if it has not already been granted.
5. Permission denied shows a clear error and does not start fake recording.
6. Permission granted starts fake recorder/encoder and moves state to listening/recording.
7. PTT stop moves state to thinking/speaking/connected and records stop latency under 300 ms.
8. Notes remain unchanged.

