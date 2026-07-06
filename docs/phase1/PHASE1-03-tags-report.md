# Phase1-03 Tags 基础能力交付报告

## 本阶段目标

把 Phase1-01/02 里的轻量 `tag_text` 标签 UI 接到真实 tag 数据上，让左侧标签面板具备基础管理能力，并保证便签标签绑定、移除、重命名、删除都通过 UseCase 进入领域层。

## 本阶段完成内容

- 左侧 tag 面板改为读取真实 `tags` 表。
- 支持在 tag 面板中新建标签。
- 支持在 tag 面板中重命名标签。
- 支持在 tag 面板中删除标签。
- 删除标签只删除 tag 关系，不删除便签。
- 便签内编辑标签时会创建缺失标签，并更新 `note_tag_cross_ref` 关系。
- 从便签内移除标签时，会同步移除对应 note-tag 关系。
- 旧的 `notes.tag_text` 数据会在启动时回填到正式 tag 表和关联表中。
- 修复详情页标签保存后仍显示“保存内容”按钮的问题。

## 新增文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/ListTagsUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/CreateTagUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/RenameTagUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/DeleteTagUseCase.kt`
- `docs/phase1/PHASE1-03-tags-report.md`

## 修改文件

- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/TagDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteTagDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/mapper/NoteMapper.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/detail/NoteDetailViewModel.kt`

## 数据库变更

本阶段没有修改 Room schema 版本。正式 tag 表和 `note_tag_cross_ref` 表在前期 schema 中已经存在，本阶段开始正式使用。

兼容策略：

- 保留 `notes.tag_text` 作为 Phase1-01 轻量输入兼容字段。
- 启动时将已有 `tag_text` 回填到 `tags` 和 `note_tag_cross_ref`。
- 后续便签保存会同步更新 `tag_text` 和正式 tag 关系。

## UI 变更

- 左侧抽屉从“标签预览”升级为真实标签管理面板。
- 标签面板新增“新建标签”输入。
- 每个 tag 支持筛选、改名、删除。
- tag 筛选使用正式 tag id，不再使用 UI 字符串临时匹配。

## 验收方式

1. 在左侧标签面板新建 `客户` 标签，列表中可见该标签。
2. 打开便签，输入标签 `客户、报价`，保存后卡片展示两个标签。
3. 回到标签面板，选择 `# 客户`，列表只显示绑定客户标签的便签。
4. 将 `客户` 重命名为 `客户A`，卡片和筛选项同步更新。
5. 删除 `客户A`，便签仍存在，但不再显示该标签。
6. 在便签详情页修改标签并保存，保存按钮应立即消失。

## 已知限制

- Phase1-03 仍未做完整 tag 管理独立页面。
- 删除 tag 当前直接执行，没有二次确认；后续可按 UX 需要补确认弹窗。
- 搜索仍未进入 Phase1-04，本阶段只保证 tag 筛选与绑定关系真实可用。

## 下一阶段建议

进入 Phase1-04：搜索与筛选。重点接入标题、正文、标签搜索，并保证搜索结果排序符合 spec 中的优先级。
