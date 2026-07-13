# 小泓便签

[English](README.md)

小泓便签是一款内置小智兼容语音助手的 Android 便签应用。它将日常便签管理与语音、文字和唤醒词交互整合在同一个应用中，助手可以查找、创建、整理和更新便签。

便签数据保存在设备本地，无网络时仍可正常管理。只有助手激活和在线对话需要网络连接。

## 架构

![小泓便签架构图](docs/architecture.png)

## 主要功能

### 完整的便签体验

- 普通便签与待办便签
- 搜索、标签、颜色、置顶以及列表或网格布局
- 归档、最近删除、恢复和彻底删除
- 批量置顶、归档、恢复、删除和添加标签
- 便签列表、筛选结果与助手操作自动同步
- 可自定义主页和侧栏颜色

### 内置语音助手

- 兼容小智的设备激活与对话服务
- 按住说话和免手持流式对话两种模式
- 可选的文字输入和屏幕对话记录
- 通过语音管理便签、待办、标签、归档、搜索和页面导航
- 连接中断后自动重连，并能从音频异常中恢复
- 流式对话中可选打断助手播报

### 本地唤醒词

- 基于 sherpa-onnx 的端侧关键词识别
- 支持预设唤醒词和 2-6 个汉字的自定义唤醒词
- 保存前可选择读音、校验模型并进行本机测试
- 应用进入后台或设备锁屏后，可通过前台服务继续监听
- 开启唤醒词后，应用重启或设备重启可自动恢复监听
- 可调整灵敏度和重复唤醒间隔

### 可确认、可追溯的助手操作

- 破坏性或目标不明确的操作必须由用户明确确认
- 待确认操作可跨进程重启保留，并会安全过期
- 支持在破坏性修改前保存便签版本快照
- 助手操作记录在有容量上限的本地命令历史中
- 重复协议请求不会导致同一操作执行两次

## 开始使用

小泓便签支持 Android 8.0（API 26）及以上系统。

1. 从源码构建并安装应用。
2. 在主页直接创建和整理便签。
3. 点击“小智”按钮激活助手。如果出现验证码，请按应用提示完成设备授权，然后再次点击按钮。
4. 首次开始语音对话时授予麦克风权限。
5. 需要免手持使用时，在设置中开启唤醒词。

应用默认使用按住说话模式；唤醒词监听只有在用户主动开启后才会运行。

## 语音交互

助手按钮会根据连接状态和当前语音模式执行对应操作：

- **按住说话：** 按住按钮开始讲话，松开发送。
- **流式对话：** 单击开始连续对话，再次单击结束。
- **唤醒词：** 前台服务正在监听时，说出设定短语即可开始流式对话。
- **文字输入：** 在设置中开启文字输入后，无需使用麦克风也可发送消息。

需要确认的操作会先在应用中展示预览，确认后才会执行。用户可以在应用内确认，也可以在当前助手会话中通过语音确认。前台服务通知本身不会直接批准任何操作。

## 隐私与权限

便签内容、标签、版本快照、待确认操作和命令历史均保存在应用的本地数据库中。唤醒词识别同样在设备端完成。只有在助手语音会话进行期间，音频才会发送至所使用的小智兼容服务，以完成语音处理和回复生成。

| 权限 | 用途 |
| --- | --- |
| 麦克风 | 语音对话、唤醒词识别和自定义唤醒词测试 |
| 通知 | 后台唤醒词监听运行时显示系统要求的前台服务通知 |
| 麦克风前台服务 | 在用户开启后维持后台唤醒词监听 |
| 开机启动 | 设备重启后恢复此前已开启的唤醒词监听 |
| 网络 | 助手激活、在线对话和工具调用 |

普通便签操作不会触发麦克风或通知权限请求；只有启用对应语音功能时才会申请相关权限。

## MCP 工具

助手通过 MCP 使用以下便签应用能力。高风险调用会返回待确认状态，在用户批准前不会修改数据。

### 便签

```text
notes.resolve
notes.search
notes.list_recent
notes.get
notes.create
notes.append
notes.delete
notes.update_title
notes.toggle_done
notes.convert_type
notes.pin
notes.replace_content
notes.restore_revision
notes.archive
notes.restore
notes.clear_done
notes.list_archived
notes.list_deleted
notes.list_todos
notes.list_done
notes.list_pinned
notes.list_by_tag
```

### 标签

```text
tags.search
tags.bind
tags.delete
tags.create
tags.rename
tags.list
```

### 应用导航

```text
ui.open_note
ui.show_confirmation
ui.show_search
ui.show_note_list
ui.show_pinned
ui.show_tag
ui.show_archive
ui.show_trash
```

### 操作确认

```text
assistant.confirm
assistant.reject
assistant.list_pending_confirmations
```

## 从源码构建

构建环境：

- 安装 Android SDK 35 的 Android Studio
- JDK 17
- Android 8.0 及以上设备或模拟器

克隆仓库并构建 Debug APK：

```powershell
git clone https://github.com/ER1C-6832/note-assistant-android.git
cd note-assistant-android
./gradlew.bat :app:assembleDebug
```

在 macOS 或 Linux 上，请使用 `./gradlew` 替代 `./gradlew.bat`。

运行主要单元测试：

```powershell
./gradlew.bat :notes-domain:testDebugUnitTest :notes-data:testDebugUnitTest :assistant-mcp-base:testDebugUnitTest :assistant-tools:testDebugUnitTest :assistant-runtime:testDebugUnitTest :assistant-wakeword:testDebugUnitTest
```

## 技术栈

应用使用 Kotlin、Jetpack Compose 和 Material 3 开发；Room 负责本地数据存储，DataStore 保存偏好设置，Hilt 提供依赖注入，OkHttp 连接小智兼容 WebSocket 服务，Android 音频 API 与 MediaCodec 负责语音传输，sherpa-onnx 用于端侧唤醒词识别。

## 项目文档

- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [便签行为规格](docs/spec/PHASE1_NOTES_SPEC.md)
- [可信操作与可追溯性规格](docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md)
- [助手运行时规格](docs/spec/PHASE3_ASSISTANT_RUNTIME_SPEC.md)
- [MCP 便签工具规格](docs/spec/PHASE4_MCP_NOTES_TOOLS_SPEC.md)
- [唤醒词规格](docs/spec/PHASE5_WAKEWORD_SPEC.md)

## 参与贡献

欢迎提交 Issue 和 Pull Request。涉及行为变化时，请简要说明用户可感知的结果及验证方式。修改助手工具时，应保留高风险操作的确认、版本快照和命令历史行为。

提交 Pull Request 前，请构建受影响的模块并运行对应单元测试，同时确保产品文档与应用实际行为一致。

## 致谢

语音运行时兼容小智协议，并参考配套的 [xiaozhi-android](https://github.com/ER1C-6832/xiaozhi-android) 项目。端侧关键词识别由 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 提供。
