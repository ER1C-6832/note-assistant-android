# Phase 5 产品体验收口实施报告

## 基线

- 仓库：`ER1C-6832/note-assistant-android`
- 提交：`2c0f9242025e3ac80928773cd739f35496444cdd`

## 设置页

设置页重写为面向普通用户的四块内容：

1. 外观：主页颜色、侧栏颜色。
2. 唤醒词：默认只显示总开关；预设、自定义、灵敏度和重复唤醒间隔进入默认收起的高级选项。
3. 语音模式：按住说话、流式对话和允许插话。
4. 显示文字对话：控制主界面的悬浮 ASR/TTS 与文本输入框。

底部保留默认关闭的开发者选项，仅显示最近 20 条命令日志。

已从产品 UI 移除：

- Phase2、Phase3、Phase4 字样。
- SQLite 数据库名称和搜索实现描述。
- “本地优先”测试状态。
- Fake/Real Runtime 切换。
- OTA、WebSocket、MCP、工具模拟器、revision 调试按钮。
- 麦克风 owner、VAD 数值、session 和内部 Gate 状态。

## 主界面助手入口

小智入口不再打开大面板。主按钮按当前状态执行：

- 未激活：执行 Real activation；需要验证码时在按钮上方展示验证码。
- 已激活但离线：手动连接或重连。
- 在线且流式模式：单击开始，再次单击结束。
- 在线且按住说话模式：按下录音，松开发送。
- 正在自动重连：按钮显示“连接”；自动重连耗尽后显示“重试”。

## 文字对话

打开设置中的“显示文字对话”后，按钮上方出现悬浮文本框：

- 显示最近一次 STT/ASR 用户文字。
- 显示最近一次助手文本回复。
- 右侧保留发送按钮。
- 音频解码统计不再写入“最近助手回复”。

原协议仍复用 `AssistantText` 事件，但控制器通过原始消息 `type=stt` 将其写入 `lastUserText`，其余 llm/text 写入 `lastAssistantText`。

## 连接恢复

自动重连由单次 120ms 重试调整为：

```text
1s -> 2s -> 4s -> 8s -> 15s
```

最多 5 次。连接成功后计数清零；用户主动断开或关闭助手会取消重连。全部失败后不长期停留在 Error，而是回到“点击小智重试”。

已激活且启用过的助手，在 App 冷启动时会尝试恢复连接。

## 唤醒词恢复

- App 启动时读取持久化开关，服务未监听时自动重新启动。
- 注册 `BOOT_COMPLETED` Receiver，在设备正常重启后恢复已开启的唤醒词前台服务。
- 设置页不再提供“暂停/恢复监听”按钮，避免用户需要手工修复内部状态。

## 修改范围

完整覆盖：

- `SettingsScreen.kt`
- `WakeWordSettingsBox.kt`
- `VoiceConversationSettingsBox.kt`
- `AssistantEntryOverlay.kt`

新增：

- `WakeWordBootReceiver.kt`
- `ic_menu_hamburger.xml`

精确修改：

- `SettingsRepository.kt`
- `NoteListScreen.kt`
- `WakeWordServiceController.kt`
- `assistant-wakeword/AndroidManifest.xml`
- `NoteAssistantApp.kt`
- `LocalAssistantController.kt`
