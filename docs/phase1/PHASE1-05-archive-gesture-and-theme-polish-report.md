# Phase1-05 收尾优化：横滑反馈、归档、主题颜色

## 背景

本轮继续收尾 Phase 1，不改变既有主体功能。目标是补齐首页手势反馈、归档入口、Tag 抽屉密度和基础主题颜色设置。

## 完成内容

1. 首页横滑反馈
   - 便签列表横滑时，列表内容会跟随手指产生横向位移。
   - 左滑在 `全部 -> 置顶 -> 待办 -> 已完成 -> 固定首页 tag` 间切换。
   - 在 `全部` 状态右滑会直接拉出 Tag 抽屉。
   - 固定首页 tag 后，已完成继续左滑可以进入固定 tag 页面，不再回到全部。

2. Tag 抽屉压缩
   - 新建标签输入框改为更矮的自定义输入框。
   - 创建按钮与输入框保持同一高度。
   - 固定筛选项更紧凑，给已创建标签列表释放更多空间。

3. 已归档
   - Tag 抽屉固定筛选区新增 `已归档`，位置在 `已完成` 和 `最近删除` 之间。
   - 归档便签不会出现在主界面、置顶、待办、已完成或 tag 筛选下。
   - 归档便签只在 `已归档` 范围中显示。
   - `已归档` 范围支持搜索和多选。
   - `已归档` 范围支持批量取消归档和删除到最近删除。
   - 普通活跃范围多选支持批量归档。

4. 背景颜色设置
   - 设置页新增主界面背景颜色选择。
   - 设置页新增 Tag 抽屉背景颜色选择。
   - 默认仍是主界面白色、Tag 抽屉暖黄色。
   - 使用现有 DataStore 保存设置。

## 修改文件

- `app-settings/src/main/java/com/er1cmo/noteassistant/app/settings/SettingsRepository.kt`
- `notes-ui/build.gradle.kts`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListState.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/settings/SettingsScreen.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/ListArchivedNotesUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/SetNoteArchivedUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`

## 说明

本轮仍然不接入语音、MCP、唤醒词、revision 或 command log。所有便签写操作仍走现有 UseCase / Repository 链路。
