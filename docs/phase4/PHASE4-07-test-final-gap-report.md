# Phase4-07 测试、查漏补缺与最终验收报告

## 状态

本覆盖包推进 Phase4-07：测试与最终报告，并补齐 Phase4 工具面中的两个遗漏：

- `notes.list_by_tag`：spec Required Read and Discovery Tool。
- `notes.list_pinned`：产品能力补齐，用于语音列出置顶便签。

## 已补工具

### notes.list_by_tag

- 风险：Low。
- 行为：按 `tag_id` 或 `tag_name/tag` 列出便签。
- 默认只查活动便签，可通过 `include_archived/include_deleted` 扩大范围。
- 不修改便签，不写入数据库。

### notes.list_pinned

- 风险：Low。
- 行为：列出置顶便签。
- 默认只查活动便签，可通过 `include_archived/include_deleted` 扩大范围。
- 不修改便签，不写入数据库。

## 新增测试

### assistant-tools

- `NoteToolRegistryPhase4Test`
  - registry lists all required Phase4 tools.
  - descriptor schema exposes risk/mutates/confirmation fields.
  - unknown tool fails closed.
  - requires_confirmation envelope preserves pending id and preview.

### assistant-runtime

- `McpProtocolClientPhase4ToolCallTest`
  - tools/list uses injected executor descriptors.
  - tools/call uses injected executor and returns structuredContent envelope.
  - no executor fails closed with `blocked / executor_unavailable`.

- `AssistantRuntimeBoundaryTest`
  - static boundary check: runtime must not import `NoteCommandService`, `notes-data`, Room, DAO, or repository implementation.

### assistant-mcp-base

- `McpPhase4MapperContractTest`
  - descriptors include schema/risk/mutation/confirmation fields.
  - result envelope preserves machine-readable fields.
  - invalid JSON maps to safe failed envelope.

## Manual acceptance checklist

### Fake runtime

1. 进入设置页 Phase3/Phase4 助手运行时。
2. 使用 Fake。
3. Fake 激活。
4. 连接当前模式。
5. 点击 `列出 Phase4 tools/list`。
6. 确认 `notes.list_by_tag` 和 `notes.list_pinned` 均出现。
7. 手动输入：

```json
{"tag_name":"Phase4","limit":10}
```

执行 `notes.list_by_tag`。

8. 手动输入：

```json
{"limit":10}
```

执行 `notes.list_pinned`。

### Real Xiaozhi manual acceptance

Real endpoint 已通过 Phase3 GateB。Phase4 最终验收应覆盖：

- Real Xiaozhi `tools/list` 能看到完整工具。
- Real Xiaozhi 可以调用低风险查询工具。
- Real Xiaozhi 可以调用中风险 mutation 工具并写 command log。
- Real Xiaozhi 调用高风险工具时返回 `requires_confirmation`。
- 语音“确认/取消”可映射为 `assistant.confirm/assistant.reject`。
- UI 在本地数据库 flow 更新后刷新。

## Phase4 completion caveat

本包补齐工具注册和测试骨架，但仍建议在真机继续跑完整 Gradle：

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-mcp-base:testDebugUnitTest :assistant-tools:testDebugUnitTest :assistant-runtime:testDebugUnitTest :app:assembleDebug --no-build-cache
```

