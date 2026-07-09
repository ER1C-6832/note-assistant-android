# Phase4-10 buildfix-02: NoteReferenceResolver visibility fix

## Problem

`:assistant-tools:compileDebugKotlin` failed because `NoteReferenceResolver` is public and its public methods exposed internal types:

- `NoteResolveRequest`
- `NoteResolveResult`
- `NoteResolveScope`

Kotlin disallows public APIs from exposing internal return or parameter types.

## Fix

`NoteReferenceResolver.kt` has been replaced so these model types are public within the module API:

- `enum class NoteResolveScope`
- `data class NoteResolveRequest`
- `data class NoteResolveResult`
- `fun String.toNoteResolveScope(...)`

The resolver behavior is unchanged.

## Expected build command

```bat
gradlew.bat clean :assistant-tools:compileDebugKotlin :assistant-tools:assembleDebug :app:assembleDebug --no-build-cache
```
