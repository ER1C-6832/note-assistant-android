# Phase5-01 KWS Migration Report

## Baseline

- Note app repository: `ER1C-6832/note-assistant-android`
- Note app baseline commit: `246be28acc1eedfff18417fb10ba4c2a3ba191d2`
- Reference repository: `ER1C-6832/xiaozhi-android`
- Reference baseline commit: `42ffbb992ad10758d750213ef7d827387451b6a6`
- Specification: Phase 5 Wake Word and Voice Conversation Specification v2

## Delivered

### KWS migration

- `WakeWordConfig`
- extensible `WakeWordPhraseType` and `WakeWordPhrase`
- three validated presets
- sensitivity presets
- cooldown configuration
- `WakeWordEvent`
- `SherpaWakeWordDetector`
- `SherpaWakeWordEngine`
- foreground service
- package-independent foreground notification
- start/update/pause/resume/stop actions

### Local resource migration

The apply script copies from the local mature reference checkout:

```text
xiaozhi-android/app/src/main/java/com/k2fsa/sherpa/onnx
xiaozhi-android/app/src/main/assets/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20
xiaozhi-android/app/src/main/jniLibs
```

It rejects missing files, likely Git LFS pointers/placeholders, missing JNI, and missing `arm64-v8a` JNI.

### Architecture

```text
WakeWordForegroundService
    -> WakeWordCoordinator
        -> StateFlow + detection SharedFlow
            -> settings UI / future runtime handoff
```

The coordinator is application-scoped through Hilt. No Compose screen is required for service detection. Phase5-01 does not inject or call `AssistantController` yet.

### Persistence

A dedicated DataStore-backed repository was added under `app-settings`:

```text
WakeWordSettingsRepository
```

It persists:

- enabled
- phrase type
- preset id
- future custom text/grammar
- sensitivity
- cooldown
- hit count
- false-trigger count
- cooldown-ignored count

A null service Intent reloads persisted settings before KWS is initialized. Invalid future custom configuration fails explicitly instead of silently falling back to a preset.

### Settings UI

The existing note app settings screen gains a Phase5-01 panel with:

- enable/disable
- preset selection
- sensitivity selection
- cooldown selection
- pause/resume
- service and microphone-owner state
- last detection and latency
- statistics
- false-trigger marking
- statistics reset

The UI requests microphone permission and Android 13+ notification permission before enabling the service.

### Android service contract

The library manifest declares:

- `RECORD_AUDIO`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`
- `POST_NOTIFICATIONS`
- non-exported microphone foreground service

## Explicitly deferred

The following are not part of this overlay:

- connecting WebSocket after detection;
- starting assistant audio after detection;
- streaming conversation;
- PTT/KWS arbitration with the assistant audio engine;
- `source=wakeword` MCP propagation;
- background confirmation handoff;
- custom wake-word grammar compiler and custom UI;
- boot receiver;
- automatic KWS resume after an assistant turn.

## Static validation completed

- Apply script Python syntax check.
- First-run and repeated-run overlay simulation.
- Pure Kotlin compilation for phrase/config/event models.
- XML parse checks.
- Source boundary scan for forbidden note/MCP/data imports.
- Required overlay and patch-marker verification.

## Required local validation

Android Studio and real-device validation remain authoritative because this environment does not contain the user's complete Android checkout, SDK state, model binaries, or JNI binaries.

Use `PHASE5_01_ACCEPTANCE_CHECKLIST.md` for the first run.
