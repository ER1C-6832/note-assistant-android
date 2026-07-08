# Phase3 GateB Activation Code and MCP Initialize Fix

## Problem

GateB real OTA could stay in the activation call for the full polling window before the UI showed the Xiaozhi activation code. This made it impossible to open the Xiaozhi website and bind the device promptly.

The real server can also send MCP JSON-RPC `initialize` before `tools/list` or `tools/call`. Phase3 previously returned `unsupported MCP method: initialize`, which can block later real tool-call boundary verification.

## Changes

- `OtaActivationClient.runRealOtaAndActivation()` now returns `OtaActivationState.Required` immediately when the OTA response contains an activation challenge.
- The returned `OtaActivationOutcome` includes `activationCode`, `websocketUrl`, and a status message with the configured authorization URL.
- The user can now open the authorization website, enter the displayed code, and then continue GateB by connecting the current Real runtime mode.
- `McpProtocolClient` now handles MCP JSON-RPC `initialize` and `notifications/initialized` safely.
- `tools/list` and `tools/call` behavior remains unchanged: Phase3-safe tools are listed, and `notes.*` / `tags.*` calls stay blocked.

## Safety boundary

This fix does not call `NoteCommandService`, does not depend on Room/DAO, and does not execute note-changing MCP tools. Real note tool execution remains Phase4.

## Acceptance

1. Switch to Real runtime.
2. Tap Real OTA.
3. If the server requires activation, the UI should quickly show `activation_code=<code>` and the status text should mention the Xiaozhi authorization URL.
4. Open the authorization URL, add/bind the device, and enter the code.
5. Tap Connect current mode.
6. Continue GateB handshake/text/audio/tool-call verification.
