# Phase4-06 buildfix-04 assistant-tools compile

## 背景

`Phase4-06 buildfix-03` 注册 Gate D 工具后，本地构建在 `:assistant-tools:compileDebugKotlin` 失败。用户日志只有 Gradle task 失败摘要，没有展开具体 Kotlin `e:` 行，因此本补丁采用保守修复：完整覆盖 `Phase4ExtendedCommandService.kt`，降低复杂实现的编译风险，并保持 Gate D 工具入口不变。

## 修复内容

- 完整覆盖 `Phase4ExtendedCommandService.kt`。
- 保留：
  - `notes.restore`
  - `notes.clear_done`
  - `tags.create`
  - `tags.rename`
  - `assistant.list_pending_confirmations`
  - extended pending confirmation confirm/reject 路径。
- 保留 `NoteCommandService` 作为既有工具 fallback。
- 不改 `assistant-runtime`，继续保持 runtime 不直接依赖 NoteCommandService。

## 备注

如果本补丁后仍有编译失败，需要提供 Kotlin 编译输出中以 `e: file:///...` 开头的第一批错误行。
