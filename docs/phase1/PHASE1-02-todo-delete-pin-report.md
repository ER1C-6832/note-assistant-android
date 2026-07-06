# Phase1-02 待办、删除、置顶交付报告

## 1. 本阶段目标

把便签的日常使用闭环补齐：待办完成状态、置顶排序、软删除、最近删除恢复，以及基础详情页动作。

本阶段仍然只做纯便签 App，不接入语音、MCP、唤醒词、revision 或 command log。

## 2. 本阶段完成内容

- 列表页待办 checkbox 可直接切换完成 / 未完成。
- 详情页可标记完成 / 取消完成。
- 详情页可置顶 / 取消置顶。
- 详情页可软删除便签。
- 标签抽屉新增“最近删除”入口。
- 最近删除中的便签可进入详情并恢复。
- 默认列表排序调整为：置顶优先，同组按 updatedAt 倒序，id 倒序兜底。
- 普通便签与待办便签切换时遵守数据规则：待办改普通会清除 isDone 和 doneAt。
- 修复详情页顶部“便签详情 / 查看内容，需要修改时再进入编辑”的多余文案。
- 修复设置入口使用字符齿轮导致的视觉问题，改为 vector 齿轮图标。

## 3. 新增文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/ListDeletedNotesUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/ToggleTodoDoneUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/SetNotePinnedUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/SoftDeleteNoteUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/RestoreDeletedNoteUseCase.kt`
- `notes-ui/src/main/res/drawable/ic_settings_gear_simple.xml`
- `docs/phase1/PHASE1-02-todo-delete-pin-report.md`

## 4. 修改文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`
- `notes-ui/build.gradle.kts`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailScreen.kt`

## 5. 数据库变更

本阶段没有新增表和字段，没有升级 Room version。

使用现有字段：

- `is_done`
- `done_at`
- `pinned`
- `deleted`
- `deleted_at`
- `updated_at`

## 6. UI 变更

- 待办卡片 checkbox 可点击。
- 已完成待办标题显示删除线，卡片弱化。
- 最近删除入口放在标签抽屉里。
- 详情页顶部去掉说明型标题，只保留返回和操作按钮。
- 设置图标替换成矢量齿轮。

## 7. 验收方式

1. 新建一条待办便签，列表卡片显示 checkbox。
2. 点击 checkbox，待办变为已完成；再点击可取消完成。
3. 打开便签详情，点击“置顶”，返回列表后它应在置顶组。
4. 再进入详情，点击“取消置顶”，列表恢复按更新时间排序。
5. 打开便签详情，点击“删除到最近删除”，返回列表后该便签消失。
6. 打开左侧标签抽屉，进入“最近删除”，能看到刚删除的便签。
7. 打开已删除便签，点击“恢复便签”，它回到全部列表。
8. 将已完成待办改为普通便签后，checkbox 消失，完成状态不残留。

## 8. 已知限制

- 删除暂时是直接软删除，没有二次确认弹窗。
- 最近删除只支持恢复，暂不支持永久删除。
- 置顶、删除、恢复的用户反馈还没有 Snackbar。
- 搜索仍未正式接入，留给 Phase1-04。

## 9. 下一阶段建议

继续 Phase1-03：正式 Tag 基础能力，包括 tag 创建、重命名、删除、绑定和筛选。
