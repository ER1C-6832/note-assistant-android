# Phase3-03 Activation Path Report

## Scope

This step adds the Phase 3 activation foundation without connecting the real WebSocket runtime yet.

Implemented:

- Device identity model and manager.
- DataStore-backed assistant runtime settings extension.
- Fake activation path for Gate A validation.
- Real OTA / activation adapter based on the audited `xiaozhi-android` flow.
- Settings runtime panel actions for identity and activation checks.
- Parser tests for OTA response shape.

## Safety Rules

Phase3-03 does not execute note tools.

- `assistant-runtime` still does not import `notes-data`, DAOs, `NoteRepositoryImpl`, or `NoteCommandService`.
- Fake and real activation only write assistant runtime settings: identity, OTA metadata, WebSocket URL/token, activation status, and masked last JSON.
- Notes are not read or mutated by activation.

## Reference Mapping

| Item | Reference | Phase3-03 implementation |
| --- | --- | --- |
| Device identity | `xiaozhi-android/.../DeviceIdentityManager.kt` | `assistant-runtime/.../identity/DeviceIdentityManager.kt` |
| OTA request payload | `xiaozhi-android/.../OtaActivationClient.kt` | `assistant-runtime/.../activation/OtaActivationClient.kt` |
| OTA response parser | `xiaozhi-android/.../OtaModels.kt` | `assistant-runtime/.../activation/OtaResponseParser.kt` |
| Activation challenge polling | `xiaozhi-android/.../OtaActivationClient.kt` | `assistant-runtime/.../activation/OtaActivationClient.kt` |
| Settings persistence | `xiaozhi-android/.../ConfigRepository.kt` | `app-settings/.../SettingsRepository.kt` |

## Manual Acceptance

1. Open Settings.
2. Enable the assistant.
3. Tap Prepare Identity.
4. Verify `device_id` and `client_id` are shown.
5. Tap Fake Activation.
6. Verify activation becomes `activated`, and a fake WebSocket URL is shown.
7. Tap Reset Identity.
8. Verify a new device id is shown and activation state returns to unknown / needs activation.
9. Optionally tap Real OTA if endpoint access is available; no WebSocket connection is attempted in this step.

## Build / Test

Suggested commands:

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```

