# Phase4-14 测试与阶段性完成报告

> 口径说明：本文件是 Phase4 工具链与 App 集成的阶段性测试完成报告，不等同于真实文本/真实语音最终验收闭环。真实 Xiaozhi 文本验收、PTT 语音验收、长时使用稳定性仍需要继续跑。

## 1. 本阶段目标

Phase4-14 的目标是把 Phase4 的“工具、确认、UI、source、边界”收束成可重复测试的契约，并给 Phase5 wakeword 入口留下稳定接口：

- registry lists all required tools
- descriptor schemas include required fields
- unknown tool fails closed
- invalid JSON returns invalid_json
- confirmation mapping tests
- architecture boundary tests
- fake runtime tools/call tests
- real runtime manual acceptance record
- UI feedback / auto confirmation manual acceptance
- Phase5 readiness note

## 2. 本包新增自动化测试

### assistant-runtime

新增：

```text
assistant-runtime/src/test/java/com/er1cmo/noteassistant/assistant/runtime/Phase4RuntimeContractTest.kt
```

覆盖：

- invalid MCP JSON-RPC payload -> invalid_json
- executor unavailable / unknown tool -> fail closed
- fake/debug `tools/call` 使用同一个 `McpProtocolClient -> McpToolExecutor` 链路
- requires_confirmation 不被当成 blocked failure
- `McpToolContext` 暴露 `voice / local_tool_simulator / wakeword`
- `AssistantState` 暴露 Phase4 工具诊断字段：
  - `phase4RealToolCallVerified`
  - `lastToolName`
  - `lastToolStatus`
  - `lastCommandLogId`
  - `lastConfirmationId`
- runtime status text 不再保留 `Phase3 边界处理 / tool_block` 用户可见语义
- `assistant-runtime` 不直接依赖：
  - `NoteCommandService`
  - `notes-domain`
  - `notes-data`
  - Room / DAO / RepositoryImpl

### assistant-tools

新增：

```text
assistant-tools/src/test/java/com/er1cmo/noteassistant/assistant/tools/Phase4ToolSurfaceContractTest.kt
```

覆盖：

- `AssistantToolsModule` 绑定所有 Phase4 必需工具与新增补充工具
- 源码中存在所有 required tool names
- 关键 descriptor schema 暴露用户可见引用字段：
  - `note_ref`
  - `note_title`
  - `title`
  - `query`
  - `exact_title`
  - `allow_multiple`
  - `auto_convert_to_todo`
  - `target_type`
  - `confirmation_id`
- registry unknown tool -> `not_implemented` / fail closed
- `requires_confirmation` envelope 保留 confirmation id / preview / affected ids / command log id
- `assistant-tools` 不直接依赖：
  - `notes-data`
  - DAO
  - Room
  - `NoteRepositoryImpl`

## 3. Required tool surface

本阶段自动化契约要求存在以下工具：

### Note read / resolve

- `notes.resolve`
- `notes.search`
- `notes.list_recent`
- `notes.get`
- `notes.list_by_tag`
- `notes.list_archived`
- `notes.list_deleted`
- `notes.list_todos`
- `notes.list_done`
- `notes.list_pinned`

### Note mutation

- `notes.create`
- `notes.append`
- `notes.update_title`
- `notes.replace_content`
- `notes.toggle_done`
- `notes.convert_type`
- `notes.pin`
- `notes.archive`
- `notes.delete`
- `notes.restore`
- `notes.restore_revision`
- `notes.clear_done`

### Tag tools

- `tags.create`
- `tags.search`
- `tags.list`
- `tags.rename`
- `tags.delete`
- `tags.bind`

### UI tools

- `ui.open_note`
- `ui.show_search`
- `ui.show_note_list`
- `ui.show_tag`
- `ui.show_archive`
- `ui.show_trash`
- `ui.show_pinned`
- `ui.show_confirmation`

### Assistant confirmation tools

- `assistant.confirm`
- `assistant.reject`
- `assistant.list_pending_confirmations`

## 4. Spec 完成定义对照

| 项目 | 当前阶段性结论 |
|---|---|
| 完整 descriptors | 自动化测试覆盖关键字段；真实工具描述仍需随使用继续打磨 |
| Gate A/B/C 工具 | 已纳入 registry contract |
| Gate D 工具或明确延期 | 已纳入 registry contract；若真实验收发现语义问题，继续 buildfix |
| mutation 走 NoteCommandService | 架构测试阻断 runtime 越界；assistant-tools 不碰 DAO/Room；具体命令仍以 command log 验证 |
| 高风险持久确认 | confirmation envelope 与 App 自动确认链路已有测试/模板 |
| 语音和 App 可确认/拒绝 | App 自动确认已做；真实语音“确认/取消”仍需 Real runtime 手工验收 |
| source=voice 日志 | Phase4-13 已准备 source 接口；本阶段用手工记录验证 Real runtime command log source |
| fake/real 共享 executor | runtime test 覆盖 `McpProtocolClient -> McpToolExecutor`；Real runtime 还需真机记录 |
| UI 自动刷新 | Phase4-11/11-buildfix 已实现；本阶段给手工验收模板 |
| 架构边界通过 | 新增源代码边界测试 |

## 5. 推荐执行命令

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-mcp-base:testDebugUnitTest :assistant-runtime:testDebugUnitTest :assistant-tools:testDebugUnitTest :app:assembleDebug --no-build-cache
```

如只跑本阶段新增测试：

```bat
gradlew.bat :assistant-runtime:testDebugUnitTest :assistant-tools:testDebugUnitTest --no-build-cache
```

## 6. Real runtime 手工验收仍未闭环

下面这些必须用全局小智入口 + Real runtime 跑，不能只用 Settings Debug panel 算通过：

- Real `tools/list`
- Real `notes.create`
- Real `notes.search`
- Real `notes.list_recent`
- Real `notes.append`
- Real `notes.toggle_done`
- Real `ui.open_note`
- Real `notes.delete -> requires_confirmation`
- Real `assistant.confirm`
- Real `assistant.reject`

记录模板见：

```text
docs/phase4/PHASE4-14-real-manual-acceptance-record.md
```

## 7. Phase5 readiness note

Phase5 的 wakeword 入口不应重做命令链路。目标接口是：

```text
wakeword detected
  -> runtime receives user intent
  -> McpProtocolClient tools/call
  -> McpToolExecutor
  -> assistant-tools
  -> NoteCommandService
  -> command log source=wakeword
```

也就是说 Phase5 主要新增 source 与入口触发方式：

- Real runtime natural speech: `CommandSource.Voice`
- Fake/debug runtime: `CommandSource.LocalToolSimulator`
- Future wakeword: `CommandSource.Wakeword`

命令执行、确认、日志、revision、UI feedback 不应复制一套。

## 8. 当前未宣称完成项

- 真实文本完整话术验收未跑完
- 真实语音 PTT 验收未跑完
- 长时间使用下的 search/list/tool loop 仍需继续观察
- 服务端是否稳定规范化“确认/取消/刚才那条”等话术仍需真实记录
- 普通便签转待办/标记完成已补工具链，但仍需要真机话术回归
