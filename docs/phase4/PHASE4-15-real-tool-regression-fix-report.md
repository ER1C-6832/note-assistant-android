# Phase4 Real Runtime 工具回归修复报告

> 基线：`ER1C-6832/note-assistant-android` main，commit `c99baefa3318304fda1a7cbca86cdbc42145ac83`
>
> 输入：Phase4 Real Runtime + DSV4 39 工具真实话术验收结果。
>
> 本补丁处理客户端可修复问题；ASR 把“归档”转写为“硅党/轨档/硅档”等问题仍需服务端/ASR 侧继续优化。

## 1. 失败归类

### A. 严重：目标便签串线

受影响工具：

- `notes.append`
- `notes.update_title`
- `notes.replace_content`
- `notes.pin`
- `notes.restore`
- `tags.bind`
- `ui.open_note`
- `notes.archive` 具有同类风险

现象：`notes.search/resolve` 返回正确目标，但后续工具操作了旧的或无关的 `note_id`。

根因：上述多数 descriptor 只接受 `note_id`，没有统一的 `note_ref/title/query` 解析和冲突校验。模型容易复用对话中较早出现的内部 ID；工具执行层又直接相信该 ID。

### B. 严重：搜索/标签/归档结果驻留

现象：工具驱动首页进入搜索或筛选后，用户清空并进入详情页，再返回首页时旧搜索/筛选重新出现。

根因：`AppNavigation` 长期保存最后一个 `NoteListExternalCommand`。列表页重新进入组合时，相同命令会再次执行。

### C. 功能语义错误：待办列表

- `notes.list_todos` 只返回未完成待办。
- `notes.list_todos`、`notes.list_done` 没有驱动对应 UI 筛选。

根因：`NotesListTodosTool` 明确过滤了 `!isDone`，且两个工具未注入 `UiCommandBus`。

### D. 结果表达不足

- `notes.list_recent` 只说数量，不说标题和正文摘要。
- `notes.get` 工具结果 JSON 有内容，但 `message` 只有“已读取便签”，导致云端回复和 App 操作卡片信息不足。
- 其他 list 工具也以数量为主。

根因：工具卡片和部分模型主要使用 `McpToolResult.message`；丰富数据只存在于 `resultJson`。

### E. ASR/云端规划问题

- “归档”被转写为“硅党/轨档/硅档”。
- “把”被转写成“8”。
- 错误转写后云端可能误调 `notes.create`。

这不是 Android 本地工具执行层能够完全修复的问题。本补丁通过 descriptor 明确“归档不是创建”并限制 `notes.create` 的适用语义，降低误规划概率，但不能改写服务端 ASR 文本。

## 2. 修复内容

### 2.1 新增统一目标解析层

新增：

```text
assistant-tools/.../notes/ResolvedNoteArguments.kt
assistant-tools/.../notes/AbstractResolvedNoteCommandTool.kt
```

统一规则：

1. 语音入口优先使用 `note_ref/note_title/target_title/exact_title/query`。
2. 唯一的用户可见标题解析结果具有最高优先级。
3. 如果请求同时携带过期 `note_id` 和正确 `note_ref`，丢弃冲突 ID，使用当前解析结果。
4. 语音入口只传 `note_id` 时默认 fail closed；只有明确 `id_is_internal=true` 才接受。
5. 单目标工具遇到多条匹配时停止执行并要求澄清。
6. 批量工具只有在 `allow_multiple=true` 或话术明确包含“全部/所有/相关”等语义时才接受多条。
7. `notes.restore` 只在 `Deleted` 范围解析，避免把 active note ID 交给恢复服务。
8. 工具结果补充 `resolved_targets`，操作卡片会显示实际目标标题。

### 2.2 接入统一解析的工具

- `notes.append`
- `notes.update_title`
- `notes.replace_content`
- `notes.pin`
- `notes.archive`
- `notes.restore`
- `tags.bind`
- `ui.open_note`

`notes.toggle_done`、`notes.convert_type`、`notes.delete` 已有独立的标题解析安全逻辑，本补丁不重复替换。

### 2.3 修复列表页驻留

`NoteListScreen` 应用外部命令后立即回报已消费，`AppNavigation` 随即清空 `noteListCommand`；离开首页路由时另有兜底清理。旧搜索、标签、归档、最近删除、置顶等命令不会在数据刷新或返回首页时重放。

### 2.4 修复待办语义和 UI

- `notes.list_todos` 返回全部 `NoteType.Todo`，包括未完成和已完成。
- `notes.list_todos` 发送 `UiCommand.ShowTodos`。
- `notes.list_done` 发送 `UiCommand.ShowDone`。
- 首页“待办”筛选调整为显示全部待办；“已完成”仍显示已完成子集。

### 2.5 丰富工具回复

- 所有 `AbstractNoteListTool` 子类在 `message` 中包含最多 6 条标题和正文摘要。
- `notes.list_recent` 从命令结果 JSON 生成可朗读摘要。
- `notes.get` 在 `message` 中包含标题、状态、标签和正文。
- 工具卡片的 detail 直接获得上述内容，无需只依赖隐藏的 JSON。

### 2.6 降低归档误调创建

- `notes.archive` descriptor 明确“归档不是创建”。
- `notes.create` descriptor 明确仅在用户明确要求新建/创建/记录时调用。执行层会拦截“轨档/硅档/硅党/归堂”等疑似归档同音前缀，并默认拒绝创建已存在的同名标题；只有显式 `confirm_create=true` 或 `allow_duplicate=true` 才放行对应例外。

## 3. 自动化测试

新增：

```text
assistant-tools/src/test/.../notes/ResolvedNoteArgumentsTest.kt
app/src/test/.../Phase4UiCommandRegressionTest.kt
```

覆盖：

- 正确 `note_ref` 覆盖冲突旧 ID。
- 语音纯 `note_id` 未标记内部来源时 fail closed。
- `id_is_internal=true` 时允许当前工具结果 ID。
- 同名多目标停止执行。
- `notes.restore` 拒绝不在最近删除范围内的 ID。
- 离开列表页后清除一次性 UI 命令。
- Todo/Done UI 命令完整路由。
- 待办筛选包含已完成和未完成。

## 4. 应用补丁

在补丁包目录执行：

```powershell
py .\apply_phase4_fix.py D:\path\to\note-assistant-android
```

或在 Linux/macOS：

```bash
python3 ./apply_phase4_fix.py /path/to/note-assistant-android
```

脚本会：

1. 将 `overlay/` 下的文件覆盖到仓库同路径。
2. 对较大的 `NoteListScreen.kt` 做精确修改。
3. 在仓库根目录创建 `.phase4_real_tool_fix_backup/` 保存原文件。
4. 执行静态完整性检查。

## 5. 构建与测试命令

Windows：

```bat
gradlew.bat --stop
gradlew.bat clean :assistant-mcp-base:testDebugUnitTest :assistant-tools:testDebugUnitTest :assistant-runtime:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-build-cache
```

快速验证：

```bat
gradlew.bat :assistant-tools:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-build-cache
```

Linux/macOS：

```bash
./gradlew --stop
./gradlew clean :assistant-mcp-base:testDebugUnitTest :assistant-tools:testDebugUnitTest :assistant-runtime:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-build-cache
```

## 6. Real Runtime 优先回归顺序

### P0：目标安全

1. `给验收客户回访补充一句，快递单号待同步。`
2. `把验收手柄记录的标题改成验收手柄升级。`
3. `把验收手柄记录的正文完整替换为手柄测试已经重新安排。`
4. `把验收客户回访置顶。`
5. `给验收手柄记录加上验收客户标签。`
6. 搜索“验收客户回访”后说：`打开这条便签。`
7. 删除“验收删除恢复”后说：`把最近删除里的验收删除恢复便签恢复回来。`

通过标准：实际 `resolved_targets`、`affected_note_ids`、页面打开目标和数据库变化必须全部指向用户说出的标题。

### P0：驻留回归

对 search、list_by_tag、list_archived、list_deleted、list_pinned、ui.show_search、ui.show_tag 分别执行：

1. 让工具切换首页视图。
2. 手动清空搜索或切到全部。
3. 打开任意详情页。
4. 返回首页。

通过标准：旧工具视图不重新出现。

### P1：读取与列表

1. `列出最近五条便签。`
2. `读取标题为验收手柄记录的便签内容。`
3. `列出所有待办便签，包括未完成和已完成的。`
4. `列出已经完成的待办。`

通过标准：语音回复和操作卡片包含实际标题/正文摘要；Todo/Done 对应 UI 正确切换。

### P1：归档 ASR

分别记录：

- 文本输入：`把验收归档记录归档。`
- PTT 原话及 ASR 文本。
- 云端实际工具序列。

文本通过、PTT 因 ASR 同音失败时，应标记为服务端 ASR 缺陷，不再归因于 Android note target resolver。

## 7. 仍需观察

- 云端是否遵循新增 schema，优先传 `note_ref`。
- 云端在只有上轮结果时，能否为 `note_id` 同时传 `id_is_internal=true`。
- ASR 对“归档/已归档/把”的中文识别。
- 长对话中模型是否仍出现 search/list 循环。
- `notes.restore_revision` 仍缺少面向用户的 revision 列表工具，保持既有产品缺口。

## 8. 本生成环境验证结果

已完成：

- `apply_phase4_fix.py` 通过 Python 语法检查。
- 覆盖脚本在模拟当前基线的目录上首次执行成功，并通过重复执行幂等测试。
- 本包 17 个 assistant-tools Kotlin 主源码文件通过接口桩编译，未发现语法、继承或构造器错误。
- `ResolvedNoteArgumentsTest`、`NotesCreateSafetyContractTest` 与 `Phase4UiCommandRegressionTest` 已随覆盖包提供。

未在本环境宣称完成：

- 完整 Android Gradle 构建。
- Hilt/KAPT 的真实工程编译。
- Real Xiaozhi 文本/PTT 真机回归。

原因是本环境无法取得完整 GitHub 工作区；最终结论必须以第 5 节命令和第 6 节真机回归为准。

## 9. 验收结论口径

本补丁通过构建、自动化测试和上述 P0 Real Runtime 回归后，可将 Phase4 状态更新为：

> 目标型 MCP 工具已具备用户可见引用解析、旧 ID 冲突防护和一次性 UI 导航；真实文本工具链完成收尾。PTT 的同音 ASR 稳定性仍作为独立服务端问题持续观察。
