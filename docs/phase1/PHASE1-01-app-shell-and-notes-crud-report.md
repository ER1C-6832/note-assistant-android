# Phase1-01 App 壳层优化与基础 Note CRUD 交付报告

## 1. 本阶段目标

本阶段只推进 Phase 1 的第一小段：纯便签 App 的基础壳层和 Note CRUD。

目标包括：

- 把 App 从 Phase 0 的 demo 壳调整成更像正式便签产品的 UI。
- 增加启动页，展示产品名“小泓便签”和宣传语“为记录而生，也为效率而来”。
- 主界面加入左上角标签/筛选入口，为后续 tag 功能打 UI 基础。
- 把语音入口改成右下角全局圆形悬浮按钮，但本阶段不接语音能力。
- 扩充便签颜色预设。
- 完成基础 Note CRUD 中的创建和编辑保存能力。

## 2. 本阶段完成内容

### 2.1 启动页

新增 `SplashRoute`：

- 中央展示图形 logo。
- 展示产品名“小泓便签”。
- 展示宣传语“为记录而生，也为效率而来”。
- 启动后短暂停留并进入便签列表页。

### 2.2 主界面 UI

调整主界面结构：

- 顶部改为：左侧标签入口、中间产品标题和宣传语、右侧设置按钮。
- 搜索框样式更轻。
- 增加“全部 / 待办 / 已完成 / 置顶”筛选 chip。
- 增加左侧浮出式标签面板，占位展示标签和筛选入口。
- 新建按钮保留为底部主要操作。
- 语音入口改为右下角圆形悬浮按钮，当前仅作为视觉占位。

### 2.3 便签卡片

调整 NoteCard：

- 使用更柔和的圆角卡片。
- 根据便签颜色字段显示不同背景色。
- 待办便签显示 checkbox。
- 已完成待办预留删除线和弱化显示逻辑。
- 展示“普通 / 待办”类型 chip。

### 2.4 编辑页

编辑页改为真实输入页面：

- 支持标题输入。
- 支持正文输入。
- 支持普通便签 / 待办便签切换。
- 支持选择预设颜色。
- 支持新建保存。
- 支持从列表点击便签进入编辑并保存修改。

### 2.5 数据层与领域层

补充基础 Note CRUD 能力：

- `getNote(id)`
- `createNote(...)`
- `updateNote(...)`

DAO 增加：

- `getNoteById(id)`
- `insertNote(note)`

Repository 继续负责在 IO dispatcher 内访问 DAO，UI 和 UseCase 不直接接触 DAO。

## 3. 新增文件

```text
notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/GetNoteUseCase.kt
notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/CreateNoteUseCase.kt
notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/UpdateNoteUseCase.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteColorPalette.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorState.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorViewModel.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/splash/SplashRoute.kt
docs/phase1/PHASE1-01-app-shell-and-notes-crud-report.md
```

## 4. 修改文件

```text
app/src/main/java/com/er1cmo/noteassistant/AppRoutes.kt
app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt
notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt
notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/NoteUseCases.kt
notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/dao/NoteDao.kt
notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListState.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListViewModel.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt
notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorScreen.kt
```

## 5. 数据库变更

本阶段没有修改 Room 表结构，也没有提升数据库版本。

只是在 `NoteDao` 中增加了查询和单条插入方法：

```text
getNoteById(id)
insertNote(note)
```

## 6. UI 变更

- 启动页新增产品名和宣传语。
- 主界面增加 tag / 筛选入口。
- 主界面增加左侧 tag 面板基础 UI。
- 主界面语音入口改为全局圆形悬浮按钮。
- 新建按钮与语音按钮做视觉区分。
- 便签颜色从少量固定色扩展为 10 个柔和预设色。
- 待办 checkbox 在便签卡片中可见。
- 编辑页从静态展示改为真实输入表单。

## 7. 验收方式

建议执行：

```bat
gradlew.bat clean :app:assembleDebug --no-build-cache
```

运行 App 后检查：

1. 启动页出现“小泓便签”和“为记录而生，也为效率而来”。
2. 启动页后进入便签列表。
3. 主界面左上角按钮可以打开标签/筛选面板。
4. 主界面右下角出现圆形语音按钮。
5. 点击“新建便签”进入编辑页。
6. 输入标题和正文，选择颜色，点击保存后返回列表。
7. 新建便签出现在列表中。
8. 点击列表中的便签进入编辑页。
9. 修改标题或正文后保存，列表内容更新。
10. 新建待办便签后，列表卡片显示 checkbox。

## 8. 已知限制

- 本阶段没有做删除、置顶切换和待办完成切换，这些放到 Phase1-02。
- 标签面板目前是 UI 基础和筛选入口，真实 tag CRUD 放到 Phase1-03。
- 搜索框目前仍是 UI 占位，真实搜索放到 Phase1-04。
- 语音圆形按钮只是全局入口视觉占位，不接语音逻辑。
- 没有做 revision、command log、MCP、wakeword，避免进入 Phase 2 及以后范围。

## 9. 下一阶段建议

Phase1-02 建议只做：

- 软删除。
- 置顶 / 取消置顶。
- 待办完成 / 取消完成。
- 列表排序和基础操作反馈。
