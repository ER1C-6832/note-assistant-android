# Phase5 文字界面与默认值修复报告

## 问题与修复

### 两个功能被错误绑定

旧实现用同一个 `assistant_text_panel_enabled` 同时控制文字记录和输入框。本次增加两个独立设置：

- `assistant_conversation_text_enabled`：默认开启。
- `assistant_text_input_enabled`：默认关闭。

### 助手回复只出现表情

Xiaozhi 的 `llm` 消息可能只是情绪 emoji，真正朗读文字位于 `tts.text`。旧路由丢弃了 `tts.text`。本次：

- `ProtocolEvent.TtsState` 保存可选文字。
- 路由读取 `tts.text`。
- 控制器累积多句 TTS 文本。
- 纯 emoji 的 `llm` 文本不进入对话框。

### 工具英文污染

旧 MCP 完成事件会把工具消息写进 `lastAssistantText`，工具卡片还可能显示 JSON、confirmation_id、log_id 和错误代码。本次移除这些产品界面字段，只显示简洁中文结果。

## 默认值

- 文字对话：开启。
- 文本输入：关闭。
- 语音模式：按住说话。
- 唤醒词：关闭。

默认值只作用于新安装或没有保存过相应设置的用户；已经明确保存的选择不会被更新强制覆盖。
