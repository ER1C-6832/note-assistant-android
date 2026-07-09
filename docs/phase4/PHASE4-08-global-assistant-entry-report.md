# Phase4-08 Global Assistant Entry Overlay

## Scope

Adds a product-facing assistant entry overlay to the app shell so users can access the assistant from notes list, detail, editor and settings screens without going into the settings debug panel.

## Added

- `AssistantEntryOverlay` composable.
- `AssistantEntryViewModel` reusing the existing `AssistantController`.
- Aurora-style compact floating button inspired by Xiaozhi Android's fluid assistant identity.
- Expanded panel with:
  - enabled / fake-real / connection / activation / audio status chips,
  - latest assistant reply,
  - runtime status text,
  - enable/disable,
  - fake-real switch,
  - activation trigger,
  - connect/disconnect,
  - text send entry,
  - press-and-hold PTT.
- App shell integration in `AppNavigation`, shown on all screens except splash.

## Boundaries

- Does not implement wakeword or foreground microphone service.
- Does not duplicate MCP runtime logic.
- Does not remove Settings debug panel.
- Reuses `AssistantController` for Fake and Real runtime.

## Acceptance

1. Open notes list after splash.
2. Confirm the Xiaozhi aurora floating button is visible.
3. Tap it and confirm expanded panel appears.
4. Enable assistant.
5. Switch Fake / Real.
6. Connect current mode.
7. Send text and verify latest assistant reply/status is visible.
8. Press and hold PTT; release to stop.
9. Navigate to note detail and editor; overlay remains available.
10. Settings debug panel still exists.

## Next

Phase4-09 should add a dedicated tool-call feedback panel so MCP tool execution results are visible as product UX, not just JSON debug output.
