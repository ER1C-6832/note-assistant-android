# Phase4-03 UI MCP Debug Panel

补充 Phase4-03 Gate A 的手工验收入口。

## 目标

- 保留 Phase3 Fake/Real runtime 状态面板。
- 在同一面板内新增 Phase4 MCP 工具模拟器。
- `tools/list` 和 `tools/call` 都经过 runtime MCP 链路，而不是直接走 Phase2 本地工具模拟器。

## 链路

```text
Settings Phase4 MCP panel
  -> AssistantController.simulateIncomingToolsList / simulateIncomingToolCall
  -> FakeXiaozhiWebSocketClient.simulateIncomingMcpRequest
  -> XiaozhiMessageRouter
  -> McpProtocolClient
  -> McpToolExecutor
  -> assistant-tools
  -> NoteCommandService / read-only UseCase
```

## 验收口径

- 顶部 Phase4 MCP 工具模拟器：用于 Phase4-03 Gate A 验收。
- 下面 Phase2 本地工具模拟器：只用于 Phase2 `NoteCommandService` 调试，不算 Phase4 MCP 验收。

## 预期

- `列出 Phase4 tools/list`：上方 last client 中包含 `tools` descriptors。
- `notes.create`：创建便签并写 command log。
- `notes.search` / `notes.list_recent` / `notes.get`：返回真实本地结果。
- `notes.append`：追加内容，并保留 Phase2 revision/log 行为。
- `notes.delete`：返回 `requires_confirmation`，不直接删除。
