# Phase1-01 视觉与启动页修正交付报告

## 本阶段目标

修复 Phase1-01 UI 调整后的三个问题：

1. Android 12+ 系统启动页先显示一个大蓝圈，导致和自定义启动页割裂。
2. 主界面设置入口使用文字/系统字符导致齿轮观感不符合预期。
3. 首页筛选区在真机上宽度紧张，tag 入口文字挤压“置顶”筛选按钮；主界面背景被误改成暖色。

## 本阶段完成内容

- 将系统启动页图标改为透明占位，避免进入自定义启动页前先出现一个大蓝圈。
- 保留自定义启动页中的蓝色圆环 logo、产品名和宣传语。
- 将主界面背景改回白色。
- 将搜索空状态等浅背景改为浅灰白，不再使用整屏暖黄色。
- 保留左侧 tag 抽屉的暖黄色背景。
- 将 tag 入口从“☰ 标签”改为仅“☰”，减少真机横向挤压。
- 设置入口改为矢量齿轮图标，不再使用系统字符。

## 新增文件

- `app/src/main/res/drawable/ic_splash_transparent.xml`
- `notes-ui/src/main/res/drawable/ic_settings_gear.xml`
- `docs/phase1/PHASE1-01-visual-tune-report.md`

## 修改文件

- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values-v31/styles.xml`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/splash/SplashRoute.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`

## 数据库变更

无。

## UI 变更

- 系统启动页尽量隐藏图标，只保留自定义启动页承担品牌展示。
- 自定义启动页背景改为白色。
- 主界面背景改为白色。
- 设置按钮改为真正的 vector 齿轮图标。
- tag 入口只显示三横线，避免挤压筛选 chip。
- tag 抽屉继续使用暖黄色系背景。

## 验收方式

1. 安装并打开 App。
2. 不应再看到一个明显的大蓝圈系统启动页。
3. 进入自定义启动页后，应看到蓝色圆环 logo、`小泓便签`、`为记录而生，也为效率而来`。
4. 主界面背景应为白色。
5. 设置入口应显示为齿轮图标。
6. 搜索框下方筛选行应完整显示：三横线、全部、待办、已完成、置顶。
7. 点击三横线后，左侧拉出暖黄色标签抽屉。

## 已知限制

- Android 12+ 系统启动页是平台强制机制，无法完全取消启动窗口，只能通过透明启动图标降低视觉存在感。
- 当前语音按钮仍是视觉占位，不接语音逻辑。

## 下一阶段建议

继续停留在 Phase1-01，确认当前 UI 和标签基础体验稳定后，再进入 Phase1-02。
