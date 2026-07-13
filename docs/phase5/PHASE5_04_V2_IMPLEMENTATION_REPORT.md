# Phase5-04 v2 实施报告

## 1. 目标

完成 Phase5 Spec 中“流式 TTS 插话”的首个产品版本，并加强异常情况下的音频资源清理。

## 2. 插话链路

```text
流式会话正在播放 TTS
-> 插话开关已开启
-> AssistantCapture 获取麦克风
-> AEC/NS + 严格 VAD 监听用户起声
-> 连续语音达到门槛
-> 停止本地 AudioTrack
-> 发送 abort(streaming_barge_in)
-> 发送 listen/start(manual)
-> 继续同一流式 session 收音
-> 1.2 秒尾静音后自动提交
-> 等待新回复
```

### 防误触参数

- 默认关闭，必须由用户在设置页开启。
- 650ms 预热，避免 TTS 刚开始时的瞬态回声。
- 至少 8 个连续 20ms 语音帧，即约 160ms 持续语音。
- 使用比普通流式 VAD 更高的 peak/RMS 门槛。
- 尾静音仍为 1.2 秒。
- 单个噪声脉冲不会触发插话。

## 3. 迟到回复隔离

插话触发后、用户新一轮语音结束前：

- 忽略旧回复迟到的非终止 TTS 状态。
- 忽略旧回复迟到的二进制 Opus 音频包。
- 旧 TTS 的 stop/end 只用于确认旧回复停止，不会启动另一个下一轮。

## 4. 来源与 KWS

- 不创建新的会话来源。
- WakeWord 启动的连续会话插话后仍为 `McpToolContext.SOURCE_WAKEWORD`。
- 按钮启动的流式会话仍为普通 voice 来源。
- KWS 在整个流式 session 中保持暂停；插话由流式 VAD 负责。

## 5. 稳定性加固

本版增加：

- `transport_closed` 时停止活动流式会话并释放麦克风。
- WebSocket Error 时取消活动会话并恢复 KWS。
- 音频错误时释放麦克风 lease，避免 owner 悬挂。
- 会话停止时失效所有迟到插话回调。
- TTS 正常结束但未发生插话时停止插话 AudioRecord，再开启普通下一轮。
- 播放空闲兜底与插话监听互斥。

## 6. 未宣称完成的稳定性项目

以下仍需真机和长时间验收，必要时继续发 Phase5-04 fix：

- 不同扬声器音量下 AEC 效果和插话阈值。
- 蓝牙 SCO、蓝牙 A2DP 和有线耳机路由。
- 电话来电、闹钟、导航等音频焦点抢占。
- 运行中撤销麦克风权限。
- 8 小时以上后台运行的耗电、发热和内存。
- 不同服务端对 `abort -> listen/start` 时序的兼容性。

## 7. 修改范围

覆盖：

- `AssistantState.kt`
- `AssistantController.kt`
- `VoiceActivityDetector.kt`
- `VoiceConversationSettingsBox.kt`

精确补丁：

- `RealAudioEngine.kt`
- `LocalAssistantController.kt`
- `SettingsScreen.kt`
- `.gitignore`

新增测试：

- `VoiceActivityDetectorBargeInTest.kt`
