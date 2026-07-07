# Phase3-02 Settings + Fake Runtime Gate A Entry Report

## Scope

This overlay exposes the Phase3 assistant runtime skeleton inside the existing settings screen without enabling real Xiaozhi network, microphone, or note tool execution.

## What changed

- Added a Hilt binding from `AssistantController` to `LocalAssistantController`.
- Reorganized the settings screen into clearer sections:
  - Notes settings.
  - Phase3 assistant runtime.
  - Phase2 command and trace debugging.
- Added a Phase3 fake runtime panel in settings.
- The panel can:
  - Enable or disable the assistant runtime.
  - Connect or disconnect the fake runtime.
  - Send a fake text conversation message.
  - Start and stop a fake push-to-talk lifecycle.
  - Simulate a `notes.delete` MCP tool call and verify it is blocked in Phase3.

## Safety notes

- This overlay does not connect to real WebSocket.
- This overlay does not request `RECORD_AUDIO`.
- This overlay does not call `NoteCommandService` from assistant runtime.
- This overlay does not mutate notes through assistant runtime.
- Existing Phase2 simulator remains unchanged and still lives in the Phase2 debug section.

## Acceptance

1. Build the app.
2. Open Settings.
3. Find `Phase3 助手运行时`.
4. Tap `启用助手`.
5. Tap `连接`.
6. Verify phase becomes connected and a fake session id is shown.
7. Send a text message and verify fake assistant text appears.
8. Tap `开始 PTT`, then `结束 PTT`, and verify state changes through listening/speaking.
9. Tap `模拟 notes.delete 工具调用并阻断` and verify the displayed assistant message says the note tool was blocked.
10. Confirm no note is deleted or changed.

## Deferred

- Real OTA/activation.
- Real WebSocket.
- Real microphone permission and AudioRecord.
- Real Opus encode/decode and playback.
- Real MCP tool execution, which belongs to Phase4.
