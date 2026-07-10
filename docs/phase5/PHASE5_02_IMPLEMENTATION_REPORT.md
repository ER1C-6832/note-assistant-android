# Phase5-02 实施报告：双语音模式、VAD 与麦克风所有权

## 1. 基线与范围

- note-assistant-android 基线：`af6453f3fb470ab510f062331ca9975a6bfe86ca`
- 产品契约：`PHASE5_WAKEWORD_SPEC.md`
- 本次只完成 Phase5-02，不把 KWS 检测事件接入助手 Runtime。

## 2. 产品行为

### 按住说话

```text
按下 -> 暂停 KWS -> 取得 AssistantCapture -> 开始录音
松开 -> 停止录音 -> listen/stop -> 等待一次回复
回复结束或保护超时 -> 恢复 KWS
```

### 流式对话

```text
单点开始
-> 暂停 KWS
-> VAD 等待起声
-> 检测用户语音
-> 检测尾静音
-> 自动停止本轮并发送 listen/stop
-> 等待文本/TTS回复
-> 回复结束后自动开始下一轮
-> 再次单点或空闲超时结束会话
-> 恢复 KWS
```

本阶段使用“每轮本地 VAD + manual listen/start/stop”的实现满足产品级流式连续对话。它不是把 PTT 永久按住，也不会在 TTS 期间保持 KWS 监听。实时插话仍留到后续收尾。

## 3. 新增核心组件

### core-common

`MicrophoneOwnershipCoordinator`

- `None`
- `WakeWordKws`
- `AssistantCapture`
- tokenized lease，旧 lease 无法释放新 owner。
- 获取超时，避免两个 AudioRecord 同时启动。

`WakeWordAudioGate`

- assistant-runtime 只依赖接口。
- 不直接依赖 wake-word Service 实现。

### app-settings

`VoiceConversationSettingsRepository`

保存：

- 默认模式：按住说话/流式对话。
- 流式空闲超时。
- 后续插话开关占位。
- 后续混合手势开关占位。

### assistant-runtime

新增：

- `VoiceInteractionMode`
- `AssistantEntrySource`
- `StreamingConversationState`
- `VoiceActivityState`
- `VoiceActivityDetector`
- `VoiceCaptureConfig`
- 流式会话专用 AssistantController API

`RealAudioEngine` 增加：

- 自适应噪声底 VAD。
- 起声检测。
- 尾静音检测。
- 无语音超时。
- 自动停止原因回调。
- TTS 播放期上行抑制能力。
- peak/rms/silent ratio 统计。

### assistant-wakeword

- `WakeWordServiceController` 实现 `WakeWordAudioGate`。
- 暂停后等待 Coordinator 确认 KWS 已释放 AudioRecord。
- `SherpaWakeWordEngine` 在打开 AudioRecord 前取得 `WakeWordKws` lease。
- Service pause/stop/destroy 强制释放 KWS owner。
- Hilt 模块绑定 Gate 接口。

### UI

全局助手入口：

- 按住模式：按下/松开。
- 流式模式：单点开始/再次单点结束。
- 权限批准后按当前设置启动正确模式。

设置页：

- 增加“Phase5-02 语音模式”。
- 显示模式、流式状态、麦克风 owner 和 VAD 状态。

## 4. 状态与防竞态

- streaming session 使用 session generation。
- 每一轮使用 turn token。
- 会话停止会使迟到 VAD callback 失效。
- 同一 turn 的自动结束只处理一次。
- 下一轮任务和回复 watchdog 均可取消。
- 用户主动停止时释放 AudioRecord 和 lease，并恢复 KWS。
- PTT 使用 press generation；若用户在 KWS 释放完成前已经松手，迟到的 start 会被取消，不会留下持续录音。
- Service `onDestroy` 不在主线程等待 KWS engine 的 Main 回调，避免销毁路径死锁。

## 5. 本阶段明确不做

- `WakeWordCoordinator.detections -> AssistantController`。
- wakeword source 向 MCP/CommandLog 的传递。
- 锁屏唤醒后自动对话。
- 流式 TTS barge-in。
- 自定义唤醒词 UI。
- 通知栏确认高风险命令。

## 6. 已执行的离线验证

- 应用脚本 Python 语法检查通过。
- 在代表性基线目录首次应用通过。
- 重复应用验证通过。
- 15 个 Kotlin overlay 文件的括号/花括号粗检查通过。
- `VoiceActivityDetector` 与语音状态模型通过 Kotlin 1.9 编译。
- `RealAudioEngine` 使用 Android/音频 stub 通过 Kotlin 1.9 编译。
- `SherpaWakeWordEngine` 使用 Android/KWS stub 通过 Kotlin 1.9 编译。
- `MicrophoneOwnershipCoordinator` 使用 coroutines classpath 通过 Kotlin 1.9 编译。

## 7. 尚需本地验证

当前环境没有完整 Android 仓库和 Android SDK，无法代替你运行完整 Gradle/Hilt/Compose 构建。以下必须由 Android Studio 验证：

- Hilt 聚合绑定。
- Compose UI 编译。
- DataStore 初始化。
- 真机 VAD 阈值。
- 真实服务端 listen/start/stop 时序。
- TTS stop 时机和下一轮启动。
- KWS/助手 AudioRecord 交接。

允许出现 5-02 fix 补丁。优先记录完整编译错误和真机日志，不要手工大范围改写架构。
