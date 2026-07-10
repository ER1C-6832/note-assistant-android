# Phase 5 Wake Word and Voice Conversation Specification

> Status: Draft v2 for Phase 5 implementation.
> Scope: Migration of the mature local KWS implementation, foreground wake-word service, wake-word settings, custom wake-word architecture, assistant runtime handoff, push-to-talk and streaming conversation modes, microphone ownership, background confirmation behavior, and long-run stability.
>
> Phase 5 adds wake-word and hands-free voice entry points. It must not rebuild MCP tools, note commands, confirmation, command logs, or revision behavior.

## 1. Purpose

Phase 5 allows the user to wake the assistant with a local wake word and then use the already-completed Phase 4 voice/MCP tool chain to operate notes.

Phase 4 completed the real assistant tool path:

```text
Real Xiaozhi runtime
    -> MCP tools/call
        -> assistant-mcp-base
            -> assistant-tools
                -> NoteCommandService / UiCommandBus
```

Phase 5 must preserve that chain and add wake-word and hands-free conversation entry:

```text
WakeWordForegroundService
    -> local sherpa-onnx KWS detection
        -> application-scoped WakeWordCoordinator
            -> AssistantController
                -> existing Real runtime listening path
                    -> existing MCP tool execution
                        -> command log source=wakeword when appropriate
```

The goal is not to create a second assistant runtime. The goal is to start or resume the existing assistant runtime from a local wake-word event and preserve both required voice interaction modes:

```text
Hold-to-talk
Streaming conversation
```

Streaming conversation means a conversation session that:

- starts by a button tap or wake-word detection;
- automatically detects when the user starts and stops speaking;
- automatically submits each completed user turn;
- plays the assistant response;
- automatically returns to listening for the next turn while the session remains active;
- ends only when the user stops it, the idle timeout expires, permissions are lost, or an unrecoverable error occurs.

The implementation may use the mature realtime/VAD path from `xiaozhi-android`, but must adapt it to the note assistant's current `AssistantController`, global assistant entry, settings, confirmation UI, and command-source model.

## 2. Current Baseline

Phase 5 starts from the following baseline:

- Phase 4 real runtime tool utterance acceptance has passed.
- Real Runtime tool calls can create, search, update, open, delete, confirm, and reject note operations through the existing MCP path.
- `assistant-wakeword` module exists.
- `WakeWordForegroundService` is currently a placeholder.
- `WakeWordConfig` exists as a minimal placeholder.
- App manifest currently does not declare foreground microphone service and notification permissions for Phase 5.
- `McpToolContext.SOURCE_WAKEWORD` already exists.
- `CommandSource.Wakeword` already exists.
- Runtime diagnostics and command source plumbing are prepared for `source=wakeword`.
- The current note assistant runtime exposes push-to-talk but does not yet expose a dedicated automatic-listening or streaming-session API.
- Phase 5 must not bypass `assistant-tools`, `NoteCommandService`, pending confirmations, command logs, or revision snapshots.

Reference implementation for direct migration and adaptation:

```text
C:\yuyinzhushou\xiaozhi-android
```

Primary wake-word source:

```text
app\src\main\java\com\er1cmo\xiaozhiandroid\wakeword
```

Relevant reference files:

```text
SherpaWakeWordDetector.kt
SherpaWakeWordEngine.kt
WakeWordConfig.kt
WakeWordEvent.kt
WakeWordForegroundService.kt
```

Relevant voice-conversation source:

```text
app\src\main\java\com\er1cmo\xiaozhiandroid\ui\main\MainViewModel.kt
app\src\main\java\com\er1cmo\xiaozhiandroid\domain\ConversationUiState.kt
app\src\main\java\com\er1cmo\xiaozhiandroid\audio\AudioEngine.kt
```

The mature reference implementation already contains:

- `Manual`, `AutoStop`, and `Realtime` voice modes;
- a wake-word entry that forces realtime listening;
- local VAD configuration;
- automatic stop behavior;
- realtime speech detection and optional TTS barge-in;
- foreground service start, update, pause, and stop actions;
- notification state;
- KWS sensitivity, cooldown, and statistics;
- sherpa-onnx model and JNI integration.

Phase 5 should migrate these proven capabilities instead of reimplementing them from scratch.

## 3. Product Contract

### 3.1 Note-first product behavior

From the user's point of view, Phase 5 must satisfy these promises:

- Wake word is explicitly opt-in.
- When wake word is enabled, Android shows a visible foreground service notification while the microphone is reserved for local wake-word listening.
- The user can choose a wake-word preset, sensitivity, cooldown, and later a validated custom wake word.
- The user can stop wake-word listening at any time.
- Wake-word detection does not silently execute note mutations.
- Wake-word detection starts the existing assistant listening flow.
- High-risk note operations after wake word still require confirmation.
- The user can inspect wake-word status, recent hit, hit count, ignored cooldown count, and false-trigger count.
- The app does not keep hidden background microphone recording.
- A wake-started conversation can create, search, update, open, and confirm note operations through the same Phase 4 MCP path.
- The app retains both hold-to-talk and streaming conversation.

### 3.2 Required voice modes

The product must expose at least two voice interaction modes:

```text
HoldToTalk
StreamingConversation
```

#### HoldToTalk

- The user presses and holds the voice button.
- Audio capture begins for that button press.
- Releasing the button ends the user turn and sends `listen/stop`.
- The assistant replies once.
- The app does not automatically open the next listening turn unless the user presses again.
- This mode is predictable and suitable for short note commands in noisy environments.

#### StreamingConversation

- The user taps the voice button once or triggers the wake word.
- The app enters an active streaming conversation session.
- VAD detects user speech and end-of-speech.
- The completed user turn is submitted automatically.
- The assistant responds.
- After the response, the app automatically re-enters listening for the next user turn.
- The user does not need to tap the button again for each round.
- The user taps the button again, says an explicit end command when supported, or waits for the idle timeout to end the session.
- High-risk confirmations remain in the same conversation session.
- Wake-word entry uses this mode by default.

An optional `AutoStopSingleTurn` compatibility mode may be retained internally from `xiaozhi-android`, but it is not required as a primary product mode. It must not replace the required multi-turn streaming mode.

### 3.3 Voice button interaction

The primary product design uses one voice button with mode-dependent behavior.

Recommended default behavior:

| Configured mode | Button label while idle | Gesture | Active label | Stop behavior |
| --- | --- | --- | --- | --- |
| Hold-to-talk | `按住说话` | press and hold | `松开发送` | release |
| Streaming conversation | `开始对话` | single tap | `结束对话` | single tap |

Rules:

- The configured default voice mode is stored in settings.
- The button label, icon, color, haptic feedback, and accessibility description must reflect the current mode.
- The active streaming state must be visually distinct from idle.
- The user must not need to infer whether the button expects a tap or a hold.
- The global assistant overlay and settings debug surface must use the same mode semantics.

A mixed gesture shortcut may be added later:

```text
single tap -> streaming conversation
long press -> temporary hold-to-talk
```

This is not the required first implementation because long-press recognition can delay capture, clip the first syllable, and reduce discoverability. If enabled:

- it must be optional;
- a haptic and visual cue must indicate when hold-to-talk capture actually begins;
- the user should speak after the cue;
- the gesture must not briefly start streaming before long-press recognition;
- accessibility alternatives must remain available.

The stable first implementation should prefer explicit mode selection in settings over hidden dual-gesture behavior.

### 3.4 Wake-word entry behavior

- A valid wake-word detection starts `StreamingConversation`.
- The wake word must not simulate a permanently pressed PTT button.
- The wake word must not require a second tap to begin the first user command.
- After the assistant response, the session automatically listens for the next user turn.
- When the streaming session ends, the app returns microphone ownership to local KWS if wake word remains enabled.

## 4. Developer Contract

From the developer's point of view, Phase 5 must satisfy these constraints:

- `assistant-wakeword` must not import `NoteCommandService`.
- `assistant-wakeword` must not import Room, DAO, `notes-data`, or note repository implementations.
- `assistant-wakeword` must not call MCP tools directly.
- `assistant-wakeword` must communicate detection events to an application-scoped coordinator.
- Only the existing assistant runtime may connect WebSocket, send listen messages, upload Opus audio, or route MCP.
- Only one component may own `AudioRecord` at a time.
- Wake-word source must be represented as `McpToolContext.SOURCE_WAKEWORD` / `CommandSource.Wakeword` when wake-word-triggered commands are executed.
- Streaming conversation must use a dedicated runtime API and state model, not UI-level repetition of PTT calls.
- KWS code proven in `xiaozhi-android` should be migrated with minimal behavioral changes; only package, persistence, coordinator, runtime, UI, and ownership adaptations are expected.

## 5. Included

- Migration audit from the mature `xiaozhi-android` wake-word and voice-mode implementation.
- sherpa-onnx KWS engine and detector integration.
- Wake-word model and keyword assets.
- sherpa-onnx JNI wrapper and required ABI libraries.
- `WakeWordForegroundService`.
- Application-scoped `WakeWordCoordinator`.
- Foreground notification channel and persistent notification.
- Wake-word start, stop, pause, resume, and update actions.
- Wake-word event and state model.
- Wake-word settings:
  - enabled
  - preset
  - custom phrase architecture
  - sensitivity
  - cooldown
  - statistics
  - false-trigger marking
- Hold-to-talk mode.
- Streaming conversation mode with automatic turn segmentation and automatic next-round listening.
- Optional internal single-turn auto-stop compatibility mode.
- Runtime handoff from wake-word detection to existing `AssistantController`.
- Per-session and per-turn source context.
- Microphone ownership coordination between KWS, push-to-talk, streaming capture, assistant listening, and TTS playback.
- Background and lock-screen confirmation behavior.
- Android permissions for microphone foreground service and notification behavior.
- Tests for state transitions, service intents, permission gating, cooldown, source mapping, custom grammar validation, streaming turn loop, and microphone ownership policy.

## 6. Excluded

- Reimplementing assistant WebSocket in `assistant-wakeword`.
- Reimplementing MCP tools in `assistant-wakeword`.
- Calling `NoteCommandService` directly from wake-word service.
- Creating notes directly from wake-word detection without the assistant runtime/tool chain.
- Hidden background microphone capture.
- Boot receiver or auto-start on device boot in the first Phase 5 delivery.
- Cloud sync.
- General Android automation.
- A second natural-language parser in the app.
- Training a new acoustic model for each custom wake word.
- A notification action that directly confirms high-risk note mutations in the first delivery.

## 7. Architecture and Global Rules

### 7.1 Boundary rule

Allowed:

```text
WakeWordForegroundService
    -> WakeWordCoordinator
        -> AssistantController
            -> existing runtime
```

UI observation:

```text
WakeWordCoordinator.state
WakeWordCoordinator.events
    -> settings / global assistant overlay / diagnostics
```

Forbidden:

```text
WakeWordForegroundService -> NoteCommandService
WakeWordForegroundService -> McpToolExecutor
WakeWordForegroundService -> XiaozhiWebSocketClient
WakeWordForegroundService -> NoteDao
WakeWordForegroundService -> Room
WakeWordForegroundService -> notes-data
```

### 7.2 Mature KWS migration rule

The following should be migrated from `xiaozhi-android` instead of rewritten:

```text
WakeWordConfig
WakeWordPreset
WakeWordSensitivity
WakeWordEvent
SherpaWakeWordDetector
SherpaWakeWordEngine
WakeWordForegroundService behavior
model assets
JNI wrapper
supported ABI libraries
foreground notification behavior
service action contract
cooldown and statistics behavior
realtime/VAD voice-mode logic where reusable
```

The following must be adapted rather than copied:

```text
package names
MainActivity notification target
Compose-owned receiver/controller
rememberSaveable configuration
reference MainViewModel
reference AppController
reference settings UI
audio ownership integration
AssistantController handoff
turn-source propagation
note confirmation presentation
```

The KWS detector, engine, model, and foreground service should remain behaviorally close to the proven reference implementation unless an adaptation is required by the note assistant architecture.

### 7.3 Application coordinator rule

Background wake-word handoff must not depend on a Compose screen being alive.

Required:

```text
WakeWordForegroundService
    -> application-scoped WakeWordCoordinator
        -> AssistantController
```

The coordinator should be injectable into the service and app surfaces. A package-scoped broadcast may remain for diagnostics or UI compatibility, but it must not be the only route that starts the runtime.

### 7.4 Dedicated automatic-listening API rule

The existing PTT API is insufficient for wake word and streaming conversation.

The runtime contract should expose explicit semantics similar to:

```kotlin
suspend fun startPushToTalk(hasRecordAudioPermission: Boolean)
suspend fun stopPushToTalk()

suspend fun startStreamingConversation(
    source: AssistantEntrySource,
    wakeKeyword: String? = null,
)

suspend fun stopStreamingConversation(
    reason: String,
)
```

A separate convenience method may be provided:

```kotlin
suspend fun startWakeWordConversation(
    detectedKeyword: String,
)
```

It must delegate to the streaming runtime path and must not simulate a permanently held PTT button.

### 7.5 Session and turn source rule

Wake-word-triggered assistant turns must be distinguishable from foreground push-to-talk.

Recommended model:

```kotlin
enum class AssistantEntrySource {
    Text,
    PushToTalk,
    StreamingButton,
    WakeWord,
}
```

```kotlin
data class AssistantConversationContext(
    val sessionId: String,
    val entrySource: AssistantEntrySource,
    val interactionMode: VoiceInteractionMode,
    val wakeKeyword: String? = null,
    val startedAt: Long,
)
```

```kotlin
data class AssistantTurnContext(
    val turnId: String,
    val conversationSessionId: String,
    val source: AssistantEntrySource,
    val startedAt: Long,
)
```

Rules:

- Hold-to-talk sets the current turn source to `PushToTalk`.
- A streaming session started by tapping the button uses `StreamingButton`.
- A streaming session started by KWS uses `WakeWord`.
- Every turn in an uninterrupted wake-started streaming session inherits `WakeWord`.
- MCP tool calls inherit the active turn context.
- Wake-started MCP commands map to `McpToolContext.SOURCE_WAKEWORD` and `CommandSource.Wakeword`.
- Reconnect may preserve the active conversation context only while the same session is being recovered.
- Session end, abort, fatal error, explicit mode change, or timeout clears the context.
- A later manual PTT turn must not incorrectly retain `wakeword`.
- Text input remains `voice` or a future explicit text source according to the existing command-source policy; it must not be mislabeled as wake word.

### 7.6 Permission rule

Phase 5 may request:

```text
RECORD_AUDIO
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MICROPHONE
POST_NOTIFICATIONS
```

Rules:

- `RECORD_AUDIO` must be granted before KWS or any voice mode starts.
- Android 13+ notification permission must be handled before claiming background wake readiness.
- Wake word cannot be enabled if microphone permission is denied.
- The app must explain that wake word uses a visible foreground microphone service.
- The service must stop if microphone permission is revoked.
- Notification denial must be shown explicitly; the app must not claim that the wake-word service is visibly active when the notification cannot be shown as expected.
- Voice mode settings may be configured without permission, but starting capture must remain blocked.

### 7.7 Foreground service rule

- Wake-word listening runs inside a foreground service.
- The notification is ongoing while the service is listening or paused.
- The notification indicates wake-word status and selected phrase.
- The service releases microphone resources on stop and destroy.
- The service uses `foregroundServiceType="microphone"` where supported.
- The first explicit service start is user initiated.
- Boot auto-start is deferred.
- `START_STICKY` or equivalent restart behavior must not silently restore default settings.

### 7.8 Persisted configuration and restart rule

Wake-word and voice-mode configuration must use the app's DataStore-backed `SettingsRepository`, not `rememberSaveable` as the source of truth.

Required persisted settings include:

```text
wakeword_enabled
wakeword_phrase_type
wakeword_preset_id
wakeword_custom_text
wakeword_custom_grammar
wakeword_sensitivity
wakeword_cooldown_ms
voice_interaction_mode
streaming_idle_timeout_ms
streaming_barge_in_enabled
mixed_gesture_shortcut_enabled
```

Recommended persisted statistics:

```text
wakeword_total_hit_count
wakeword_false_trigger_count
wakeword_cooldown_ignored_count
```

Runtime-only state includes:

```text
current_service_state
current_microphone_owner
current_pause_reason
current_conversation_session
last_runtime_error
```

When the service is recreated with a null or incomplete Intent:

1. load the latest persisted wake-word configuration;
2. verify `wakeword_enabled`;
3. validate the phrase and grammar;
4. verify permissions;
5. start KWS only if all checks pass.

Rules:

- `enabled=false` must never restart listening.
- Invalid or missing custom grammar must stop with an explicit error.
- The service must not silently fall back to `小智`.
- A custom phrase must remain active after process/service recreation.

### 7.9 Microphone ownership rule

Microphone ownership and assistant presentation state are separate concepts.

Required microphone owner model:

```kotlin
enum class MicrophoneOwner {
    None,
    WakeWordKws,
    AssistantCapture,
}
```

Required wake-word pause reason model:

```kotlin
enum class WakeWordPauseReason {
    None,
    PushToTalk,
    StreamingConversation,
    AssistantSpeaking,
    Cooldown,
    AudioFocusLoss,
    PhoneCall,
    PermissionMissing,
    ServiceStopping,
    Recovering,
    Error,
}
```

Rules:

- At most one `MicrophoneOwner` exists at a time.
- KWS listening owns `WakeWordKws`.
- PTT and streaming audio uplink own `AssistantCapture`.
- TTS playback does not own the microphone.
- If streaming barge-in is disabled, assistant capture may stop or pause during TTS.
- If streaming barge-in is enabled, assistant capture may remain active during TTS, but KWS remains paused.
- KWS pauses before assistant capture opens.
- Assistant capture releases before KWS resumes.
- Service stop releases all KWS resources.
- Errors release ownership and enter a recoverable state.
- Duplicate wake events inside cooldown are ignored and counted.

### 7.10 TTS, KWS, and barge-in rule

KWS and streaming barge-in are different mechanisms.

Default safety policy:

```text
assistant TTS begins
    -> KWS remains paused
    -> no self-wake from the assistant saying the wake word
```

Streaming conversation may optionally support user barge-in using the assistant capture/VAD path:

```text
streaming session active
    -> TTS playing
    -> user speech detected after grace period
    -> stop TTS
    -> send abort/user_interruption
    -> continue current streaming conversation
```

Rules:

- KWS must not be used to interrupt TTS.
- KWS remains paused for the full active streaming session.
- `streaming_barge_in_enabled` is a separate setting.
- Barge-in requires echo/self-trigger suppression, a TTS grace period, and cooldown.
- The first stable build may default barge-in off even if the mature reference code is migrated.
- If barge-in is off, TTS completes before automatic next-round listening resumes.

### 7.11 Background high-risk confirmation rule

High-risk note operations keep the existing Phase 4 confirmation model.

If a wake-started streaming session is still active:

- the assistant may speak the confirmation summary;
- the user may say `确认` or `取消`;
- the server must call the existing `assistant.confirm` or `assistant.reject` path;
- only a unique, unexpired pending confirmation may be acted on without clarification.

If the active voice session has ended or the user must inspect details:

- the notification may show `有待确认操作`;
- tapping the notification opens the app and restores the existing confirmation UI;
- the first Phase 5 delivery must not expose a direct notification `确认执行` action;
- expired pending confirmation follows existing Phase 4 behavior;
- background state must never silently auto-confirm.

### 7.12 Gate interpretation rule

Gate A through Gate F are acceptance checkpoints, not separate architectures and not reasons to reimplement the same system repeatedly.

Implementation should migrate the mature KWS and voice path once, while each gate verifies a larger behavior set.

Migration audit and implementation may proceed together as long as the migration/adaptation record is updated before the corresponding acceptance gate is closed.

## 8. Custom Wake Word Architecture

### 8.1 Product requirement

The final product must support validated custom wake words from settings.

Phase 5 must establish the extensible architecture even if the first UI initially exposes only presets.

### 8.2 Data model

Do not permanently model the wake word as a closed enum or a single ambiguous string.

Recommended model:

```kotlin
enum class WakeWordPhraseType {
    Preset,
    Custom,
}
```

```kotlin
data class WakeWordPhrase(
    val id: String,
    val type: WakeWordPhraseType,
    val displayText: String,
    val grammar: String,
)
```

```kotlin
data class WakeWordConfig(
    val phrase: WakeWordPhrase,
    val sampleRate: Int = 16_000,
    val frameMs: Int = 100,
    val cooldownMs: Long,
    val callbackDelayAfterHitMs: Long,
    val keywordsScore: Float,
    val keywordsThreshold: Float,
    val numTrailingBlanks: Int,
    val sensitivityLabel: String,
)
```

The detector must consume `phrase.grammar` directly. It must not convert unknown text through `WakeWordPreset.fromDisplayName()` and silently fall back.

### 8.3 Presets

Initial validated presets remain:

```text
小智
小智小智
小智同学
```

Their existing hand-tuned grammar variants may be migrated directly.

### 8.4 Custom grammar compiler

Required boundary:

```kotlin
interface WakeWordGrammarCompiler {
    fun compile(displayText: String): WakeWordGrammarCompileResult
}
```

A successful result includes:

```text
normalized display text
one or more pronunciation/token variants
compiled sherpa keyword grammar
warnings
```

Validation rules for the first custom version:

- 2 to 6 common Chinese characters.
- No blank input.
- No pure numbers.
- No punctuation-only input.
- No silent fallback to a preset.
- Chinese-English mixed phrases may be rejected until explicitly supported.
- Every generated token must exist in the packaged `tokens.txt`.
- Multiple-pronunciation characters must produce selectable or clearly reported candidates.
- The grammar must successfully create a test KWS stream before it can be saved.
- Invalid phrases preserve the previous working configuration.

Recommended settings flow:

```text
choose Custom
    -> enter phrase
        -> check availability
            -> select pronunciation when needed
                -> test locally
                    -> save displayText + grammar
                        -> ACTION_UPDATE service
```

Example product UI:

```text
唤醒词
○ 小智
○ 小智小智
○ 小智同学
○ 自定义

自定义唤醒词：[ 小泓同学 ]
[检查可用性] [本机测试] [保存]
```

Custom wake-word support does not require training a new acoustic model for each phrase. It uses the existing KWS model and dynamic stream grammar, subject to token coverage and validation.

## 9. Voice Conversation Runtime Contract

### 9.1 Required mode model

Recommended product model:

```kotlin
enum class VoiceInteractionMode {
    HoldToTalk,
    StreamingConversation,
}
```

An internal compatibility value may exist:

```kotlin
AutoStopSingleTurn
```

but it must not weaken the two required modes.

### 9.2 Hold-to-talk lifecycle

```text
Idle
    -> button down
        -> pause KWS
            -> acquire AssistantCapture
                -> send listen/start manual
                    -> upload Opus
                        -> button release
                            -> stop capture
                                -> send listen/stop
                                    -> Thinking
                                        -> Speaking
                                            -> return Idle or KWS
```

Rules:

- Release always attempts to stop capture.
- Too-short or silent input produces a clear nonfatal result.
- Hold-to-talk is one turn only.
- Source is `PushToTalk`.
- If wake word is enabled, KWS resumes after the reply and cooldown.

### 9.3 Streaming conversation lifecycle

```text
Idle or KwsListening
    -> tap button or wake word
        -> create conversation session
            -> pause KWS
                -> connect/activate if needed
                    -> acquire AssistantCapture
                        -> ListeningForSpeech
                            -> UserSpeaking
                                -> EndOfSpeech
                                    -> submit completed turn
                                        -> Thinking
                                            -> Speaking
                                                -> ListeningForSpeech
                                                    -> next turn
```

The user remains in the same streaming session across turns.

Required states may include:

```text
StreamingInactive
StreamingStarting
StreamingListeningForSpeech
StreamingUserSpeaking
StreamingSubmittingTurn
StreamingThinking
StreamingSpeaking
StreamingWaitingForNextTurn
StreamingStopping
StreamingRecovering
StreamingError
```

Rules:

- The session does not require a new wake word for each turn.
- End-of-speech is detected automatically.
- The implementation may use the mature local VAD/realtime protocol from `xiaozhi-android`.
- The product contract is automatic turn completion and automatic next-round listening, regardless of whether capture remains continuously open internally.
- If the protocol requires `listen/stop` per turn, the runtime sends it after VAD end-of-speech.
- If realtime protocol supports continuous segmentation, the runtime may keep the session open while still exposing explicit turn states.
- TTS completion automatically transitions back to listening when the session remains active.
- An idle timeout ends the session if no user speech arrives after the configured duration.
- Explicit stop, assistant disable, permission loss, phone call, audio-focus policy, fatal WebSocket error, or unrecoverable audio error ends the session.
- Recoverable connection loss may preserve the session intent and resume after reconnect.
- The streaming button remains an explicit stop control.

### 9.4 VAD contract

VAD must distinguish at least:

```text
waiting for speech
speech detected
speech active
end of speech
no useful speech
```

Required behavior:

- background noise alone must not immediately submit an empty turn;
- minimum speech duration and minimum useful audio checks are applied;
- end-of-speech silence threshold is configurable internally;
- VAD status is visible in diagnostics;
- a VAD callback must not race with manual session stop;
- only one turn submission may occur for one end-of-speech event;
- repeated callbacks after stop are ignored;
- the response watchdog recovers from a turn that never receives a server response.

### 9.5 Wake-word to streaming handoff

```text
KWS detects phrase
    -> KWS engine stops and releases AudioRecord
        -> coordinator records WakeWord context
            -> optional local acknowledgement tone
                -> runtime starts StreamingConversation
                    -> first user command is captured
```

Rules:

- The acknowledgement tone must not be uploaded as user speech.
- The app must wait until KWS releases microphone ownership.
- The first user syllable must not be clipped.
- If connection is not ready, the runtime preserves a pending wake-started streaming intent and begins capture only after connection succeeds.
- If activation requires user action, the app reports activation state and returns to KWS after recovery rather than looping.
- The wake-started session source remains `WakeWord` until that streaming session ends.

### 9.6 Button and mode setting contract

Settings must provide:

```text
默认说话模式
○ 按住说话
○ 流式对话
```

Recommended default:

```text
流式对话
```

because it aligns with wake-word hands-free use and multi-turn note operations.

UI rules:

- In hold-to-talk mode, the main button handles press/release.
- In streaming mode, the main button handles tap start/tap stop.
- A mode badge or secondary text may display the active mode even though there is only one primary button.
- Changing mode while audio capture is active first stops the current interaction safely.
- The next interaction uses the newly persisted mode.
- Wake-word entry always uses streaming conversation even if the manual button default is hold-to-talk.
- A later optional mixed-gesture shortcut must not replace the settings-level mode selector.

## 10. Migration Audit Contract

Migration begins from the latest stable `xiaozhi-android` implementation. The audit is an adaptation record, not a requirement to redesign mature KWS code.

Minimum audit table:

| Source item | Migrate? | Target module | Adaptation required | Acceptance |
| --- | --- | --- | --- | --- |
| `WakeWordForegroundService` | yes | `assistant-wakeword` | package, notification target, Hilt, persisted config, coordinator | service/background |
| `SherpaWakeWordEngine` | yes | `assistant-wakeword` | ownership lease, direct config grammar | KWS/audio |
| `SherpaWakeWordDetector` | yes | `assistant-wakeword` | asset paths, direct grammar, validation | KWS/custom |
| `WakeWordConfig` | yes | `assistant-wakeword` / `app-settings` | phrase model, persistence | settings |
| `WakeWordEvent` | yes | `assistant-wakeword` | timestamps/config/session metadata | diagnostics |
| model assets | yes | app or wakeword assets | packaging and size check | KWS |
| JNI wrapper | yes | `assistant-wakeword` or dedicated sherpa package | package adaptation | KWS |
| JNI libs | yes | app or wakeword packaging | ABI check | install |
| foreground notification | yes | `assistant-wakeword` | note app target and pending state | service |
| wake-word settings logic | adapt | `app-settings` + settings UI | DataStore and custom phrase | settings |
| reference Compose controller | no direct copy | none | replace with coordinator | background |
| `Manual` voice mode | adapt | runtime/app | map to HoldToTalk | voice |
| `Realtime` voice mode | adapt | runtime/app | map to StreamingConversation | voice |
| `AutoStop` voice mode | optional | runtime | internal compatibility only | optional |
| reference VAD logic | yes/adapt | audio/runtime | current audio engine APIs and tests | streaming |
| wake-word-to-runtime bridge | adapt | app/runtime coordinator | dedicated streaming API | runtime |
| generic Xiaozhi UI | no | none | not migrated | none |
| MCP and note commands | no | existing modules | reuse unchanged | tools |

The audit must record:

- Required assets and final paths.
- Required native libraries and supported ABIs.
- Manifest changes.
- Runtime dependencies added to `assistant-wakeword`.
- How KWS events cross from service to coordinator.
- How microphone ownership is leased and released.
- How streaming VAD is migrated.
- How custom grammar is represented.
- How wake-started session source reaches MCP command logs.

## 11. Behavior Cards

### WW-01 Enable Wake Word

- Trigger: User turns on wake word in settings.
- Preconditions:
  - Assistant feature is enabled or can be enabled.
  - `RECORD_AUDIO` permission is granted.
  - Notification permission is granted when required for expected visibility.
  - KWS assets and JNI are present.
  - Selected phrase grammar is valid.
- Flow:
  1. Persist wake-word enabled setting.
  2. Start `WakeWordForegroundService`.
  3. Service loads persisted config.
  4. Service creates notification channel.
  5. Service starts in foreground.
  6. Service initializes KWS engine.
  7. Service acquires `WakeWordKws`.
  8. Service enters listening state.
  9. UI and notification show listening status.
- Failure:
  - Missing permission: setting returns off and shows permission message.
  - Missing model assets: service stops and reports configuration error.
  - Invalid custom grammar: service keeps previous valid config or stops explicitly.
  - AudioRecord unavailable: service enters error and releases resources.
- Acceptance:
  - Given app is foreground, when wake word is enabled, notification appears and status shows KWS listening.

### WW-02 Disable Wake Word

- Trigger: User turns off wake word in settings or notification action.
- Flow:
  1. Persist enabled=false.
  2. Send stop action to service.
  3. Stop KWS engine.
  4. Release `WakeWordKws`.
  5. Remove foreground notification.
  6. Stop service.
- Acceptance:
  - Given KWS is listening, when disabled, no microphone resource remains active.

### WW-03 Detect Wake Word

- Trigger: Local KWS detects configured phrase.
- Flow:
  1. Service validates cooldown.
  2. Service publishes `WakeWordEvent.Detected`.
  3. Service releases KWS microphone ownership.
  4. Coordinator records the wake-started conversation context.
  5. UI/log/notification displays detected phrase.
  6. Runtime handoff starts streaming conversation.
- Acceptance:
  - Detection itself does not mutate notes.
  - One detection creates at most one streaming session.

### WW-04 Detect While Backgrounded or Locked

- Trigger: Local KWS detects phrase while app UI is not foreground.
- Flow:
  1. Foreground service receives detection.
  2. Service calls application-scoped coordinator.
  3. Coordinator starts or resumes runtime if policy permits.
  4. Assistant enters streaming listening.
- Acceptance:
  - Background handoff works without a Compose receiver being alive.
  - Notification remains visible.
  - Lock-screen detection starts the existing runtime, not a second runtime.

### WW-05 Wake Word Runtime Handoff

- Trigger: Valid wake-word event.
- Flow:
  1. Pause/stop KWS and release microphone.
  2. Ensure assistant is enabled.
  3. Ensure Real runtime is selected.
  4. Resolve activation or report required user action.
  5. Connect Real runtime if needed.
  6. Start `StreamingConversation`.
  7. Carry wake-word conversation context.
- Forbidden:
  - Wake-word service directly opening WebSocket.
  - Wake-word service directly uploading Opus.
  - Wake-word service directly calling MCP tools.
  - Wake-word service calling PTT start without an automatic stop/session policy.
- Acceptance:
  - Wake word leads to streaming listening.
  - A wake-started note command logs `source=wakeword`.

### WW-06 Hold-to-Talk

- Trigger: User presses and holds the single voice button while mode is HoldToTalk.
- Flow:
  1. Pause KWS.
  2. Acquire assistant microphone ownership.
  3. Start manual listen/audio upload.
  4. Release button.
  5. Stop recording and send stop.
  6. Play response.
  7. Resume KWS after cooldown if enabled.
- Acceptance:
  - Press/release semantics remain available after Phase 5.

### WW-07 Streaming Conversation

- Trigger: User taps the voice button while mode is StreamingConversation, or wake word is detected.
- Flow:
  1. Start one streaming conversation session.
  2. Listen for speech.
  3. Detect end-of-speech automatically.
  4. Submit the turn.
  5. Play assistant response.
  6. Automatically listen for the next turn.
  7. Repeat until stopped or timed out.
- Acceptance:
  - At least two user turns can complete without another button tap or wake word.
  - Each completed turn produces at most one tool/action sequence.
  - The stop button ends the session and returns ownership safely.

### WW-08 Microphone Pause and Resume

- Trigger: PTT, streaming session, TTS policy, service stop, phone call, or audio focus change.
- Flow:
  - PTT or streaming start: release KWS before assistant capture.
  - Session stop: release assistant capture before KWS resume.
  - TTS with barge-in disabled: pause assistant capture and keep KWS paused.
  - TTS with barge-in enabled: keep assistant capture only; KWS remains paused.
  - Service stop: release KWS permanently.
- Acceptance:
  - No flow produces two active `AudioRecord` owners.

### WW-09 False Trigger Marking

- Trigger: User marks recent wake as false trigger.
- Flow:
  1. Increment false-trigger count.
  2. Store timestamp and recent phrase if permitted.
  3. Optionally recommend a more conservative sensitivity or longer phrase.
- Acceptance:
  - Statistics update and no note command is executed by the marking action.

### WW-10 Background Confirmation

- Trigger: A wake-started note command returns `requires_confirmation`.
- Flow:
  1. Preserve the existing pending confirmation.
  2. Speak the summary if the streaming session is active.
  3. Accept spoken confirm/reject only through existing assistant tools.
  4. Show confirmation state in notification.
  5. Tap notification to open existing app confirmation UI.
- Acceptance:
  - No automatic confirmation.
  - No direct note mutation from service.
  - Voice confirm/reject and UI confirm/reject converge on the same persisted command.

### WW-11 Update Preset or Custom Phrase

- Trigger: User saves a new validated preset or custom phrase.
- Flow:
  1. Compile and validate grammar.
  2. Persist phrase and grammar atomically.
  3. Send service update.
  4. Stop current KWS stream.
  5. Reinitialize with new phrase.
  6. Report active phrase.
- Acceptance:
  - Invalid input does not replace the last working phrase.
  - Process/service restart preserves the selected phrase.

## 12. Settings Specification

Phase 5 settings must provide:

### Wake-word section

- Wake word enabled switch.
- Phrase selector:
  - `小智`
  - `小智小智`
  - `小智同学`
  - `自定义`
- Custom phrase input and validation state.
- Custom pronunciation selection when needed.
- Local phrase test action.
- Sensitivity selector:
  - Conservative
  - Standard
  - Sensitive
- Cooldown selector or numeric setting.
- Current service state.
- Last status message.
- Last detected phrase.
- Last detection latency.
- Hit count.
- Cooldown ignored count.
- False-trigger count.
- Mark false trigger action.
- Reset stats action.

### Voice interaction section

- Default voice mode:
  - Hold-to-talk
  - Streaming conversation
- Streaming idle timeout.
- Streaming barge-in enabled switch.
- Optional mixed gesture shortcut switch, disabled by default.
- Current conversation session state.
- Current microphone owner.
- Current VAD state.

Rules:

- Wake word always starts streaming conversation.
- Manual button uses the configured default mode.
- Settings must be persisted in DataStore.
- Permission-denied state must be explicit and recoverable.
- Product-facing settings should be separate from Phase 2/3/4 developer diagnostics.
- Advanced score/threshold values may remain developer-only while friendly sensitivity presets remain product-facing.

## 13. Notification Specification

Foreground notification must show:

- Wake-word service state.
- Current selected phrase.
- Sensitivity label.
- Hit count or last detection state.
- Whether KWS is listening, paused for conversation, or in error.
- Pending confirmation indicator when applicable.
- Tap action opens the app.
- Stop action may be added when stable.

Notification channel:

- No sound by default.
- No vibration by default.
- Visible and ongoing while active.
- Appropriate foreground service category.

Notification actions must not directly execute high-risk note mutations in the first delivery.

## 14. Android Manifest and Permission Contract

Manifest must declare as needed:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Service declaration:

```xml
<service
    android:name="..."
    android:exported="false"
    android:foregroundServiceType="microphone" />
```

Rules:

- `POST_NOTIFICATIONS` is handled on Android 13+.
- Android 12+ foreground service restrictions use a user-initiated initial start.
- Do not add boot receiver in the first Phase 5 delivery.
- Internal broadcasts, if retained, are package-scoped or not exported.
- PendingIntent flags comply with supported Android versions.

## 15. Acceptance Gate Plan

These are acceptance checkpoints over one migrated/adapted architecture.

### Gate A: KWS and settings

Acceptance:

- Mature sherpa KWS code and assets are migrated.
- Preset detection works.
- Settings persist across app restart.
- Foreground notification is visible.
- Start, update, pause, and stop work.
- Detection updates coordinator state.
- Custom phrase data model and validation boundary exist.

### Gate B: Background service

Acceptance:

- App background and lock-screen detection work.
- Coordinator handoff does not depend on Compose.
- Service restart reloads persisted config.
- Stop releases microphone.
- Permission revocation is handled.

### Gate C: Both voice modes

Acceptance:

- Hold-to-talk still works.
- Streaming conversation can be started and stopped with one button.
- Streaming mode automatically detects end-of-speech.
- Streaming mode automatically listens for the next turn.
- Wake word starts streaming mode.
- At least two consecutive turns succeed without another wake word or tap.

### Gate D: Wake-triggered note tools and confirmation

Acceptance:

- Wake word followed by a normal command can create or search a note.
- Command log shows `source=wakeword`.
- A second turn in the same wake-started streaming session still shows wakeword source.
- High-risk operation returns confirmation.
- Spoken and UI confirmation use existing Phase 4 paths.

### Gate E: Audio ownership and self-trigger prevention

Acceptance:

- PTT pauses KWS.
- Streaming pauses KWS.
- KWS does not self-wake from TTS.
- Optional streaming barge-in does not use KWS.
- No simultaneous `AudioRecord` owners.
- Rapid repeated wake words are counted as cooldown ignored.
- Audio errors recover or stop cleanly.

### Gate F: Long-run product stability

Acceptance:

- Continuous run test passes for the chosen duration.
- No unbounded logs, wake loops, duplicate turns, or repeated service crashes.
- Battery and heat are observed and documented.
- Service handles process death according to Android constraints.
- Bluetooth/headset/phone-call/audio-focus interactions are documented or safely blocked.
- Custom phrase and voice mode survive process recreation.
- Streaming idle timeout returns to KWS correctly.

## 16. Test Acceptance Suite

### Unit tests

- WakeWordConfig maps presets, custom phrase, sensitivity, and cooldown.
- Custom grammar compiler validates token coverage.
- Invalid custom phrase preserves previous configuration.
- Cooldown suppresses repeated detections.
- False trigger updates statistics.
- Service intent parser handles start, stop, pause, resume, and update.
- Null service Intent reloads persisted config.
- Permission denied prevents service start.
- Missing assets returns recoverable error.
- Audio ownership policy allows only one owner.
- Wake-word pause reason is independent from microphone owner.
- Wake-started session maps to `McpToolContext.SOURCE_WAKEWORD`.
- Wake-started command logs use `CommandSource.Wakeword`.
- Session end clears wake-word source.
- Later PTT is not mislabeled wakeword.
- Hold-to-talk press/release produces one turn.
- Streaming VAD end-of-speech produces one turn submission.
- Streaming response completion reopens next-round listening.
- Streaming stop cancels pending VAD callbacks.
- Streaming idle timeout ends the session.
- Background confirmation never auto-confirms.
- Button state and labels match configured mode.

### Integration tests where feasible

- Start service with fake detector.
- Emit detection event.
- Verify coordinator receives event without Compose.
- Verify KWS releases ownership before assistant capture.
- Verify wake detection starts streaming runtime API.
- Simulate two streaming turns.
- Verify KWS resumes only after session end.
- Verify service restart restores custom phrase.
- Verify pending confirmation appears in coordinator/UI state.
- Verify notification tap opens the confirmation surface.

### Manual tests

- Foreground wake detection.
- Background wake detection.
- Lock-screen wake detection.
- Hold-to-talk while KWS enabled.
- Streaming conversation started by tap.
- Streaming conversation started by wake word.
- Two or more automatic rounds.
- Automatic end-of-speech.
- Streaming idle timeout.
- TTS playback with barge-in off.
- TTS playback with barge-in on when enabled.
- Wake-triggered note creation.
- Wake-triggered note search/open.
- Wake-triggered high-risk confirmation.
- Spoken confirm and reject.
- UI confirm after tapping notification.
- Stop service from settings/notification.
- Permission revoke during service run.
- Preset change while service runs.
- Custom phrase validation, test, save, and restart.
- Bluetooth/headset/phone-call/audio-focus behavior.

## 17. Error Handling

| Condition | Required behavior |
| --- | --- |
| No microphone permission | Do not start KWS or voice capture; show permission state. |
| Notification permission denied | Do not claim background readiness; guide the user. |
| Missing model assets | Stop KWS and show asset error. |
| JNI/library load failure | Stop KWS and show engine error. |
| Invalid custom phrase | Keep previous valid phrase; show validation details. |
| Unsupported custom tokens | Reject save; never silently fall back. |
| AudioRecord unavailable | Release owner and show recoverable audio error. |
| Wake during active conversation | Ignore as duplicate or count according to cooldown; do not start a second session. |
| KWS hears TTS | KWS should be paused; treat any event as a safety failure. |
| User speech during TTS with barge-in off | Wait until TTS completes. |
| User speech during TTS with barge-in on | Apply grace period, stop TTS, abort response, continue streaming. |
| Repeated wake during cooldown | Count ignored event; do not start second session. |
| Runtime activation required | Show activation state; return to KWS after safe recovery. |
| WebSocket connect fails | Recover or return to KWS after delay; preserve no stale audio owner. |
| VAD never sees speech | End or remain waiting until idle timeout; do not submit empty command. |
| VAD emits duplicate end events | Submit only once. |
| Streaming response timeout | Abort/recover and either resume listening or end session according to policy. |
| Permission revoked during service | Stop service and release microphone. |
| Phone call/audio focus loss | Pause or stop according to safe policy; never fight for microphone. |
| Pending confirmation while backgrounded | Speak/notify; never auto-confirm. |
| Service recreated with null Intent | Load persisted config and validate before start. |

## 18. Phase Completion Definition

Phase 5 is complete when:

- Wake-word feature is opt-in and controlled from settings.
- Foreground microphone service is implemented with visible notification.
- Mature sherpa-onnx KWS code is migrated from `xiaozhi-android`.
- Preset wake words work locally.
- Extensible preset/custom phrase data model is implemented.
- Custom grammar compilation and validation are available for product completion.
- App foreground, background, and lock-screen detection are manually verified.
- Background handoff works through an application-scoped coordinator.
- Hold-to-talk remains functional.
- Streaming conversation supports automatic end-of-speech and automatic next-round listening.
- Wake-word detection starts streaming conversation.
- Wake-triggered note creation works through existing MCP tools.
- Wake-triggered high-risk operation requires existing confirmation.
- Command logs distinguish `source=wakeword`.
- Wake-word session context is cleared correctly after session end.
- KWS and assistant capture never own microphone simultaneously.
- TTS does not self-trigger KWS.
- Optional streaming barge-in is isolated from KWS.
- Service restarts with persisted configuration rather than defaults.
- Service stops cleanly and releases audio resources.
- Android notification and foreground service permissions are handled.
- No forbidden dependency from `assistant-wakeword` to note data or command execution exists.
- Acceptance Gates A through F are closed with reports.
- Phase 6 can focus on product polish rather than rebuilding wake-word or voice-conversation architecture.

## 19. Traceability

This spec refines:

- `docs/DEVELOPMENT_PLAN.md` Phase 5.
- `docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md`.
- `docs/spec/PHASE3_ASSISTANT_RUNTIME_SPEC.md`.
- `docs/spec/PHASE4_MCP_NOTES_TOOLS_SPEC.md`.
- `docs/phase4/PHASE4-13-diagnostics-phase5-interface-report.md`.
- `docs/phase4/PHASE4-14-test-completion-report.md`.
- `docs/phase4/PHASE4-15-real-tool-regression-fix-report.md`.

Reference implementation:

- `C:\yuyinzhushou\xiaozhi-android\app\src\main\java\com\er1cmo\xiaozhiandroid\wakeword`
- `C:\yuyinzhushou\xiaozhi-android\app\src\main\java\com\er1cmo\xiaozhiandroid\ui\main\MainViewModel.kt`
- `C:\yuyinzhushou\xiaozhi-android\app\src\main\java\com\er1cmo\xiaozhiandroid\audio\AudioEngine.kt`

When Phase 5 behavior changes, update this spec before or with the implementation report.
