# xiaozhi-android

xiaozhi-android is an Android native implementation demo of a Xiaozhi-style voice assistant client. It focuses on verifying that the core py-xiaozhi interaction model can be migrated to Android with Kotlin, Jetpack Compose, Android audio APIs, WebSocket communication, Android native MCP tools, and local wake-word detection.

This repository is a technical validation project rather than a general-purpose assistant product. It is intended to serve as a stable Android assistant core for future app-specific integrations.

## Features

- Native Android client written in Kotlin and Jetpack Compose
- Device identity generation and local configuration persistence
- OTA / activation flow support
- WebSocket connection and Xiaozhi protocol message handling
- Text-triggered conversation
- Microphone uplink with Opus encoding
- TTS downlink playback with Opus decoding
- Conversation state management and reconnect handling
- Android native MCP protocol layer and local tool dispatcher
- MCP tool cards and tool history UI
- Modern assistant UI with animated Aurora Fluid visual state
- Manual, auto-stop and realtime conversation modes
- Local sherpa-onnx wake-word detection
- Wake-word settings for preset, sensitivity and cooldown
- Foreground service based wake-word listening
- Android 13+ notification permission handling
- Notification status for wake-word preset, sensitivity and hit count

## Architecture

```text
app/
├── audio/                 AudioRecord, AudioTrack, Opus encode/decode and audio engine
├── core/                  AppController and application-level event coordination
├── data/                  DataStore configuration, identity and OTA activation
├── domain/                Conversation state and UI state models
├── mcp/                   Android native MCP server, registry and tools
├── network/               WebSocket client and Xiaozhi protocol messages
├── protocol/              Protocol client abstraction
├── ui/                    Compose navigation, main screen, settings and visual components
└── wakeword/              sherpa-onnx KWS engine and foreground wake-word service
```

The application is intentionally implemented as a native Android client rather than a Python wrapper. UI operations, voice-triggered operations and MCP tool calls are routed through Android-side state and service components.

## Wake Word

The local wake-word pipeline uses sherpa-onnx KWS assets placed under the Android assets and JNI library folders. The current preset options are:

- 小智
- 小智小智
- 小智同学

Wake-word listening is hosted by an Android foreground service. When enabled, Android shows a notification indicating microphone usage and the current wake-word configuration.

## MCP Tools

The Android MCP layer supports JSON-RPC style tools over Xiaozhi WebSocket messages. The included tools focus on Android-native capabilities such as device information, battery, network, audio volume, app opening, flashlight, clipboard, settings and related status queries.

High-risk tools are designed to require confirmation before execution.

## Build

Open the project with Android Studio and run the `app` module on a physical Android device.

Recommended environment:

- Android Studio
- Kotlin / Gradle Android project support
- Android device with microphone permission granted
- Network access to the Xiaozhi-compatible server endpoint

The project contains Gradle wrapper scripts:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Local Files and Secrets

Do not commit local machine files or runtime secrets, including:

- `.idea/`
- `local.properties`
- real access tokens
- generated private activation data
- local development notes
- phase delivery reports

The public repository keeps only the source code, build files, scripts and stable documentation needed to build and understand the demo.

## Status

This repository is considered a completed Android assistant core demo. Future work should focus on bug fixes and extracting reusable assistant modules for app-specific integrations, rather than expanding it into a general-purpose assistant.
