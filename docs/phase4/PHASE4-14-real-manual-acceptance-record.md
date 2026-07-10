# Phase4-14 Real runtime 手工验收记录模板

> 使用方式：每条都从全局小智入口触发，Runtime 选择 Real。不要用 Settings Debug panel 手填 JSON 代替真实验收。

## 环境

- 日期：
- 设备：
- App commit：
- Runtime：Real
- Xiaozhi server：
- WebSocket URL/token 来源：OTA / 手动配置
- 麦克风权限：已授权 / 未授权

## 1. Real tools/list

- 用户动作：连接 Real runtime 后等待服务端 tools/list 或手动触发真实服务端列工具
- 实际 tool/method：
- 结果：通过 / 失败
- 备注：

## 2. Real notes.create

- 话术：小智，帮我记一下明天上午十点联系客户
- 服务端实际 tool：
- arguments：
- result.status：
- command_log_id：
- source 是否为 voice：是 / 否
- App UI 是否出现新便签：是 / 否
- 结果：通过 / 失败
- 备注：

## 3. Real notes.search + UI 自动搜索

- 话术：小智，搜索客户相关的便签
- 服务端实际 tool：
- arguments：
- result.status：
- 首页搜索框/列表是否自动显示客户结果：是 / 否
- 结果：通过 / 失败
- 备注：

## 4. Real notes.list_recent

- 话术：小智，最近的便签有哪些
- 服务端实际 tool：
- arguments：
- result.status：
- 是否返回 title/content/type/done 等丰富字段：是 / 否
- 结果：通过 / 失败
- 备注：

## 5. Real ui.open_note

- 话术：小智，打开刚才那条便签
- 服务端实际 tool：
- arguments：
- result.status：
- App 是否进入详情页：是 / 否
- 结果：通过 / 失败
- 备注：

## 6. Real notes.append

- 话术：小智，给刚才那条补充一句，快递单号待同步
- 服务端实际 tool：
- arguments：
- result.status：
- command_log_id：
- source 是否为 voice：是 / 否
- 详情页内容是否追加成功：是 / 否
- 结果：通过 / 失败
- 备注：

## 7. Real notes.toggle_done / notes.convert_type

- 话术：小智，把刚才那条标记完成
- 服务端实际 tool：
- arguments：
- result.status：
- 如果原来是普通便签，是否转为 todo 且 done=true：是 / 否 / 不适用
- command_log_id：
- source 是否为 voice：是 / 否
- 结果：通过 / 失败
- 备注：

## 8. Real notes.delete -> requires_confirmation + App 自动弹框

- 话术：小智，删除刚才那条便签
- 服务端实际 tool：
- arguments：
- result.status：
- confirmation_id：
- command_log_id：
- App 是否自动弹确认，不依赖额外 ui.show_confirmation：是 / 否
- 结果：通过 / 失败
- 备注：

## 9. App 点确认 -> assistant.confirm / command confirmation

- 操作：在 App 自动确认框点“确认执行”
- 实际执行路径：assistant.confirm / commandService.confirmPendingCommand
- result.status：
- command_log_id：
- 便签是否进入最近删除：是 / 否
- 结果：通过 / 失败
- 备注：

## 10. Real assistant.reject

- 前置：重新发起一次删除，但不要点确认
- 话术：小智，取消刚才的删除
- 服务端实际 tool：
- arguments：
- result.status：
- 如果只有一个 pending，是否自动拒绝最近一个：是 / 否
- 如果多个 pending，是否要求澄清：是 / 否 / 不适用
- 便签是否保留未删除：是 / 否
- 结果：通过 / 失败
- 备注：

## 11. UI feedback / Operation banner

- 操作：执行 create/search/delete/confirm/reject 任意一个工具
- lastToolName：
- lastToolStatus：
- lastCommandLogId：
- lastConfirmationId：
- 首页全局助手入口是否显示工具结果：是 / 否
- 结果：通过 / 失败
- 备注：

## 12. 总结

- 通过项：
- 失败项：
- 需要 buildfix：
- 是否可以进入 Phase5 wakeword 前置开发：是 / 否
