# Phase5-04 v3 Acceptance Checklist

## A. Build

```bat
cd /d C:\yuyinzhushou\note-assistant-android
gradlew.bat --stop
gradlew.bat ^
  :assistant-runtime:testDebugUnitTest ^
  :assistant-wakeword:testDebugUnitTest ^
  :notes-data:testDebugUnitTest ^
  :app:assembleDebug ^
  --no-build-cache
```

Expected:

- `McpRequestDeduplicatorTest` passes.
- No Hilt constructor error for `AssistantSystemAudioCoordinator`.
- No Room query validation error for `pruneKeepingNewest`.

## B. Normal regression

1. Preset and custom wake words still start streaming conversation.
2. Two consecutive wakeword-source turns succeed.
3. PTT still produces one turn only.
4. Streaming button still produces multiple turns.
5. TTS barge-in still stops TTS and enters the next listening turn.
6. High-risk note operations still require Phase 4 confirmation.

## C. Runtime microphone permission revocation

1. Start a streaming conversation.
2. While listening, revoke microphone permission from Android Settings.
3. Return to the app.

Expected:

- recording and playback stop;
- state does not remain in Listening, Thinking, or Speaking;
- microphone owner becomes `None`;
- KWS does not restart while permission is missing;
- no duplicate tool call occurs.

Grant the permission again and wait about two seconds.

Expected:

- the app returns to standby;
- enabled KWS can listen again;
- the interrupted streaming session is not automatically replayed.

## D. Audio focus and phone-call proxy

During streaming or TTS playback:

1. Start another app that takes exclusive audio focus, or receive/place a phone call.
2. Wait for the competing audio session to finish.

Expected:

- assistant capture/playback stops safely;
- the old turn is aborted;
- no stale TTS packets restart Speaking;
- KWS resumes only after `AudioManager.mode` returns to normal;
- no microphone lease is left behind.

No phone-state permission should be requested.

## E. Bluetooth and wired route

Test with Bluetooth headset or wired headset:

1. Start a streaming session.
2. Disconnect the active headset while listening or speaking.
3. Reconnect it.

Expected:

- route removal ends the active session safely;
- no crash or double AudioRecord;
- reconnect does not replay the interrupted command;
- KWS returns after the recovery guard;
- a newly started session uses the available route.

## F. KWS self-recovery

Exercise at least one route change while KWS is Listening.

Expected:

- repeated read failure changes the notification to recovery state;
- retry delays are bounded;
- the service returns to Listening without a second foreground service;
- missing permission does not create an infinite restart loop.

## G. Duplicate wake event

Trigger the same wake phrase repeatedly within two seconds.

Expected:

- at most one KWS-to-streaming handoff starts;
- no second acknowledgement tone;
- no duplicate streaming session id.

## H. Duplicate MCP request

Use the existing MCP debug path or transport replay to deliver the same `tools/call` request id twice in one session.

Expected:

- the second request receives the cached response;
- only one command log is inserted;
- create/delete/tag actions execute only once.

A new WebSocket session using the same numeric request id must still execute normally because the dedup key includes session and conversation.

## I. Command-log cap

Generate or seed more than 2,000 command logs.

Expected:

- the newest 2,000 remain;
- oldest logs are pruned;
- current command log ids remain valid;
- note revisions and pending confirmations remain available.

## J. Soak test

Run for at least two hours with:

- KWS enabled;
- at least ten wake sessions;
- PTT and streaming mixed;
- two network disconnects;
- one headset route change;
- one permission revoke/restore cycle.

Pass criteria:

- no service crash;
- no duplicate streaming session;
- no duplicate tool execution;
- no permanent Listening/Thinking/Speaking state;
- no microphone owner left after session end;
- notification matches KWS Listening, Paused, Recovering, or Error state.
