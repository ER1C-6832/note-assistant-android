# Phase1-02 详情编辑合并与更新策略修复报告

## 本阶段目标

继续收敛 Phase1-02：待办、删除、置顶，以及便签详情/编辑体验。修复用户反馈的设置图标、更新时间语义、详情页交互分裂问题。

## 本阶段完成内容

1. 设置入口改回真正的齿轮 vector 图标。
2. 调整更新时间语义：只有标题、正文、标签变化才刷新 `updatedAt`。
3. 完成勾选、取消完成、颜色变化、置顶/取消置顶、软删除、恢复不再刷新 `updatedAt`。
4. 详情页与编辑体验合并：进入便签后即可直接编辑标题、正文和标签。
5. 删除详情页顶部的“便签详情 / 查看内容，需要修改时再进入编辑”文案。
6. 详情页下方集中放置：普通/待办切换、完成/取消完成、置顶/取消置顶、改变颜色、删除/恢复。
7. 新增颜色选择子页面。点击颜色立即生效，返回后回到详情/编辑页。
8. 列表卡片不再把“已完成”当成标签 chip 展示，待办状态只通过 checkbox 和完成样式表达。

## 新增文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/ObserveNoteUseCase.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteColorPickerViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteColorPickerScreen.kt`
- `docs/phase1/PHASE1-02-detail-editor-and-update-policy-report.md`

## 修改文件

- `app/src/main/java/com/er1cmo/noteassistant/AppRoutes.kt`
- `app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`
- `notes-ui/build.gradle.kts`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailScreen.kt`
- `notes-ui/src/main/res/drawable/ic_settings_gear_simple.xml`

## 数据库变更

无 schema 变更。

## UI 变更

- 详情页顶部只保留“返回”。
- 详情页主体改成可直接编辑的便签卡片。
- 颜色选择移入单独子页面。
- 红色删除按钮更醒目。
- 设置图标使用真正齿轮。

## 验收方式

1. 打开列表，点击任意便签进入详情/编辑页。
2. 点击标题/正文/标签可直接编辑，修改后出现保存入口。
3. 只勾选待办完成，列表排序不因 `updatedAt` 改变而跳动。
4. 只改变颜色，列表排序不因 `updatedAt` 改变而跳动。
5. 详情页点击“改变颜色”，选择颜色立即生效，返回后仍在详情/编辑页。
6. 点击删除后便签进入最近删除；最近删除中可以恢复。
7. 设置按钮显示齿轮，而不是亮度/太阳图标。

## 已知限制

- 删除目前仍是直接软删除，没有二次确认弹窗。
- 颜色页选择后立即保存，没有撤销入口。
- 标签仍是 Phase1-01/02 的轻量 `tag_text`，正式 Tag CRUD 留到 Phase1-03。

## 下一阶段建议

继续 Phase1-02 小范围修 UI 和交互细节，确认稳定后再进入 Phase1-03 Tag 正式能力。
