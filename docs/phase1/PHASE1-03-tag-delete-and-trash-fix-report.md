# Phase1-03 标签与最近删除修复报告

## 本阶段目标

继续完善 Phase1-03 的真实 Tag 能力，并修复最近删除区域的交互边界。

## 本阶段完成内容

1. 左侧标签抽屉支持纵向滚动，标签数量很多时可以下滑查看全部标签。
2. 删除标签前增加二次确认，明确提示删除后该标签会从所有便签中移除且不能直接恢复。
3. 选择某个标签后，如果该标签下没有便签，点击创建会自动把该标签写入新建便签的标签输入框。
4. 底部“+ 新建”在标签筛选状态下也会自动带入当前标签。
5. 最近删除中的便签改为只读，不再允许编辑标题、正文、标签、类型、颜色、置顶或完成状态。
6. 最近删除中的便签支持复制内容。
7. 最近删除中的便签新增“彻底删除”，并带二次确认。
8. 修复详情页保存标签后“保存内容”按钮偶发不消失的问题：保存后重新读取最新便签快照并重置输入状态。

## 新增文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/PermanentlyDeleteNoteUseCase.kt`
- `docs/phase1/PHASE1-03-tag-delete-and-trash-fix-report.md`

## 修改文件

- `app/src/main/java/com/er1cmo/noteassistant/AppRoutes.kt`
- `app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailScreen.kt`

## 数据库变更

没有新增表或字段。新增了 DAO 查询：

- `DELETE FROM notes WHERE id = :id`，仅用于最近删除中的彻底删除。

## UI 变更

- 标签抽屉内容支持滚动。
- 标签删除有确认弹窗。
- 标签空结果页的创建按钮会带入当前标签。
- 最近删除详情页变为只读，只保留复制、恢复和彻底删除。

## 验收方式

1. 创建多个标签，打开左侧抽屉，确认可以上下滚动查看所有标签。
2. 删除标签时应先出现确认弹窗，取消不会删除，确认才会删除。
3. 点击一个没有便签的标签，点击创建，进入新建页后标签输入框应自动填入该标签名。
4. 删除一条便签进入最近删除，打开该便签后标题、正文、标签不可编辑。
5. 最近删除详情页点击复制内容，能复制标题、正文和标签。
6. 最近删除详情页点击彻底删除，需要确认；确认后该便签不再出现在最近删除。
7. 修改标签并保存后，“保存内容”按钮应立即消失。

## 未做内容

- 搜索增强不在本次范围，留给 Phase1-04。
- 多选批量操作不在本次范围，留给 Phase1-05。
