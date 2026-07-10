# Phase5-02 Build Fix 01 Report

## Failure

Kotlin compilation reported at the two PTT `startRecording` call sites:

- `No value passed for parameter 'onEncodedFrame'`
- `VoiceAutomaticStopReason` was inferred where `ByteArray` was expected

## Root cause

`RealAudioEngine.startRecording` has this relevant parameter order:

```kotlin
fun startRecording(
    nowMillis: Long = System.currentTimeMillis(),
    onEncodedFrame: (ByteArray) -> Boolean,
    captureConfig: VoiceCaptureConfig = VoiceCaptureConfig.Manual,
    onVoiceActivity: (VoiceActivitySnapshot) -> Unit = {},
    onAutomaticStop: (VoiceAutomaticStopReason) -> Unit = {},
)
```

The two old PTT calls used a trailing lambda:

```kotlin
realAudioEngine.startRecording(nowMillis()) { packet -> ... }
```

After `onAutomaticStop` became the last function parameter, Kotlin assigned that trailing lambda to `onAutomaticStop`. The required `onEncodedFrame` was therefore missing, and the lambda parameter was inferred as `VoiceAutomaticStopReason`.

## Fix

Both PTT calls now use named parameters:

```kotlin
realAudioEngine.startRecording(
    nowMillis = nowMillis(),
    onEncodedFrame = { packet -> ... },
)
```

The streaming call already names `onEncodedFrame`, `captureConfig`, `onVoiceActivity`, and `onAutomaticStop`, so it is unchanged.

## Scope

Changed file only:

```text
assistant-runtime/src/main/java/com/er1cmo/noteassistant/assistant/runtime/controller/LocalAssistantController.kt
```

No KWS, VAD, streaming state-machine, WebSocket, MCP, note tool, or UI behavior is changed.
