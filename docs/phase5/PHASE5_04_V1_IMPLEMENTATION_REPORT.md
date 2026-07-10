# Phase5-04 v1 实施报告

## 范围

本版聚焦“自定义唤醒词完整闭环”和主页 vector 齿轮恢复。TTS 插话、电话/音频焦点、蓝牙路由和长时间稳定性属于 Phase5-04 后续补丁。

## 自定义唤醒词链路

```text
输入 2～6 个汉字
-> pinyin4j 生成带声调拼音和多音字候选
-> 常见多音字展开候选（最多 16 个）
-> 拆分为 sherpa token
-> 校验模型 tokens.txt
-> 创建 KeywordSpotter 测试 stream
-> 可选 10 秒本机说话测试
-> 保存 customText/customGrammar
-> ACTION_UPDATE 重载前台服务
```

## 安全边界

- 只有“检查可用性”通过的当前读音候选才能保存。
- 输入变化或切换读音后，必须重新检查。
- 无效输入、token 缺失、stream 初始化失败和保存失败均不会覆盖正式配置。
- 不存在静默回退到“小智”。
- 本机测试前临时暂停正式 KWS，测试完成后只在原本处于监听状态时恢复。

## 齿轮

主页 `HeaderBar` 的文字“设置”改为 `ic_settings_gear.xml` VectorDrawable，保留 48dp 点击区域和 `contentDescription="设置"`。
