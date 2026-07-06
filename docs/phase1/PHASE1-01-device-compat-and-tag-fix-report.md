# Phase1-01 设备兼容与标签修复报告

## 本阶段目标

继续停留在 Phase1-01，不推进 Phase1-02。集中修复真机测试反馈中的三个问题：

1. 三星 S20 / Android 12 上筛选栏文字换行，导致“置顶”竖排、按钮变高。
2. 待办便签出现重复“待办”标签，切换为普通便签后仍残留“待办”标签。
3. 标签抽屉打开后，系统返回手势会直接退出 App，而不是先关闭抽屉。

同时补一个交互调整：列表点击便签先进入只读详情页，再由详情页进入编辑页，避免误触后直接进入编辑状态。

## 本阶段完成内容

### 1. 筛选栏兼容修复

- 将筛选栏改为横向可滚动容器。
- 将 Material `FilterChip` 替换为自定义 `FilterPill`。
- 对筛选文字强制 `maxLines = 1`、`softWrap = false`。
- 保留 tag 入口为单独的三横线按钮。

这样在较窄屏幕、较大字体或部分 Android 12 设备上，筛选项会横向滚动，而不是压缩换行。

### 2. 待办标签重复修复

- 列表卡片不再因为便签类型是 Todo 而额外显示一个“待办”chip。
- `待办 / todo / 普通 / normal` 作为系统类型词，不再作为普通标签展示。
- 新建或编辑便签时，标签清洗逻辑会过滤这些系统类型词。
- Demo 数据中的“联系王总”不再把“待办”写入 `tagText`，待办状态只由 `type = todo` 表示。

### 3. 抽屉返回手势修复

- 标签抽屉打开时注册 `BackHandler`。
- 用户按返回键或侧滑返回时，优先关闭标签抽屉。
- 只有抽屉关闭后，再次返回才会走页面返回或退出逻辑。

### 4. 便签详情页

- 新增只读详情页。
- 列表点击便签后先进入详情页。
- 详情页提供“编辑便签”按钮，再进入编辑页。

## 新增文件

```text
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailState.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailViewModel.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailScreen.kt
docs/phase1/PHASE1-01-device-compat-and-tag-fix-report.md
```

## 修改文件

```text
app/src/main/java/com/er1cmo/noteassistant/AppRoutes.kt
app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt
notes-ui/build.gradle.kts
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt
notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/mapper/NoteMapper.kt
notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt
```

## 数据库变更

无新增数据库版本。此次只调整标签清洗和展示逻辑。

## UI 变更

- 筛选栏可横向滚动，防止旧系统或窄屏换行。
- 便签卡片不再显示由类型派生的“待办”标签。
- 新增只读详情页。
- 标签抽屉支持返回键 / 侧滑返回关闭。

## 验收方式

1. 在三星 S20 / Android 12 上打开首页，确认“置顶”不再竖排。
2. 打开待办便签，确认卡片上不会出现两个“待办”。
3. 将待办便签改成普通便签后，确认卡片上不会残留“待办”标签。
4. 打开标签抽屉，执行系统返回或侧滑返回，确认只关闭抽屉，不直接回桌面。
5. 点击便签卡片，确认进入详情页；点击“编辑便签”才进入编辑页。

## 已知限制

- 此阶段仍不做真正的 tag 表 CRUD。
- 此阶段仍不做待办勾选切换逻辑。
- 此阶段仍不做删除、置顶切换、多选和搜索实装。

## 下一阶段建议

继续在 Phase1-01 内完成体验确认；确认稳定后再进入 Phase1-02：待办完成状态、软删除、置顶切换。
