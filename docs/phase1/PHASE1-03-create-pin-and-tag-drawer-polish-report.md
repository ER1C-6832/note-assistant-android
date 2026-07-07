# Phase1-03 小修：创建置顶、已完成样式与标签抽屉滚动

## 本阶段目标

继续收敛 Phase1-03 的 tag 基础体验问题，同时补齐创建便签时的置顶选项。

## 本阶段完成内容

1. 新建便签页新增置顶选项：
   - 可选择“不置顶 / 置顶”。
   - 保存新便签后，如果选择置顶，会通过 `SetNotePinnedUseCase` 设置为置顶。

2. 已完成待办视觉优化：
   - 标题继续删除线。
   - 正文也增加删除线。
   - 卡片整体弱化保持不变。

3. 标签抽屉滚动范围调整：
   - `全部 / 置顶 / 待办 / 已完成` 固定在上方。
   - `最近删除`、创建标签入口也固定在上方。
   - 只有“已创建标签”列表区域可滚动。

## 新增文件

无。

## 修改文件

- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`

## 数据库变更

无。

## UI 变更

- 新建便签页新增“列表位置”分组。
- 待办已完成时正文也显示删除线。
- 标签抽屉内系统筛选固定，创建标签列表单独滚动。

## 验收方式

1. 打开新建便签页，选择“置顶”，保存后回到列表，新便签应进入置顶分组。
2. 创建待办便签并勾选完成，标题和正文都应显示删除线，卡片整体变暗。
3. 创建多个 tag，打开左侧标签抽屉，系统筛选项保持在上方，只有 tag 列表区域滚动。

## 已知限制

- 新建便签仍采用独立编辑页；详情页已有内联编辑能力，但新建页暂时保留当前结构。

## 下一阶段建议

继续根据真机反馈收敛 Phase1-03，确认 tag CRUD 稳定后再进入 Phase1-04 搜索与筛选。
