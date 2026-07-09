# Phase4-08 buildfix-02: Compact Fluid Aurora Assistant Button

## Purpose

Replace the Phase4-08 sweep-gradient ring button with a compact Aurora Fluid Gradient assistant button derived from the `xiaozhi-android` product visual language.

## Files changed

- `app/src/main/java/com/er1cmo/noteassistant/assistantui/AssistantEntryOverlay.kt`

## Key changes

- Replaced `AssistantAuroraButton` with `CompactAssistantAuroraButton`.
- Removed sweep-gradient ring animation.
- Added Canvas-based 2-3 feathered radial-gradient blob rendering.
- Added continuous frame-time drive via `produceState` and `withFrameNanos`.
- Added assistant state mapping with `AssistantState.toAuroraTarget()`.
- Added state-specific color/motion targets:
  - disabled/off: slate gray, slow, low alpha
  - connecting/activating/reconnecting: blue + amber
  - connected/online: blue + lilac
  - listening/recording: peach + blue, breathing perturbation
  - thinking/uploading: blue + lilac, opposing orbit
  - speaking/playing: peach + lilac + amber third blob
  - error/audio error: rose red + slate gray
- Kept the global entry panel behavior intact:
  - expand/collapse
  - enable/disable
  - switch Fake/Real
  - activate
  - connect/disconnect
  - send text
  - PTT with microphone permission request

## Notes

This overlay does not modify global theme colors. The Aurora palette is kept as local constants in `AssistantEntryOverlay.kt`.

Android compilation was not run in the container.
