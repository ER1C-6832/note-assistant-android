# Phase1-01 修正交付报告：启动页、主界面布局与标签输入

## 1. 本阶段目标

在不进入 Phase1-02、不接语音、不做 MCP 的前提下，修正 Phase1-01 中暴露出的启动页、主界面布局、标签入口和编辑页标签保存问题。

## 2. 本阶段完成内容

- 将启动页中的“泓”字图形移除，改为蓝色圆环 logo。
- 将系统启动页背景、Compose 启动页背景和主界面背景统一为暖色底。
- 主界面顶部改为小 logo + 小泓便签 + “语音智能的便签 App”。
- 设置入口从文字按钮改为右上角小齿轮按钮。
- 标签入口移动到搜索框下方，并放在“全部 / 待办 / 已完成 / 置顶”筛选 chip 左侧。
- 标签面板从小弹窗改为左侧滑出抽屉，高度占满屏幕，宽度不占满屏幕，使用暖黄色系背景。
- 语音按钮保留全局圆形入口，但移除“声”字，暂时留空。
- 新建便签按钮从全宽大按钮改为底部较小的胶囊按钮。
- 编辑页新增“标签”输入框，支持用顿号、逗号、空格或 # 分隔多个标签。
- 修复“便签类型被误认为标签”的问题：普通/待办不再作为所有卡片的伪标签展示。
- 标签改为每条便签独立保存，不会因为修改某一条便签类型而影响其他便签的标签展示。

## 3. 新增文件

- `app/src/main/res/values-v31/styles.xml`
- `docs/phase1/PHASE1-01-ui-and-tag-fix-report.md`

## 4. 修改文件

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/java/com/er1cmo/noteassistant/di/DatabaseModule.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/splash/SplashRoute.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/components/NoteCard.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorScreen.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorViewModel.kt`
- `notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/editor/NoteEditorState.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/repository/NoteRepository.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/CreateNoteUseCase.kt`
- `notes-domain/src/main/java/com/er1cmo/noteassistant/notes/domain/usecase/UpdateNoteUseCase.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/entity/NoteEntity.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/db/NoteDatabase.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/mapper/NoteMapper.kt`
- `notes-data/src/main/java/com/er1cmo/noteassistant/notes/data/repository/NoteRepositoryImpl.kt`

## 5. 数据库变更

- `notes` 表新增 `tag_text` 字段，用于 Phase1-01 阶段先保存每条便签的轻量标签文本。
- 数据库版本从 `1` 升级为 `2`。
- 新增 `MIGRATION_1_2`：

```sql
ALTER TABLE notes ADD COLUMN tag_text TEXT NOT NULL DEFAULT ''
```

这个修正保留现有测试便签，不做破坏性清库。

## 6. UI 变更

- 启动页 logo 改为蓝色圆环，无文字图形。
- 主界面改为暖色背景。
- 顶部标题减轻字重，并移除宣传语，改为产品描述。
- 标签入口调整到搜索框下方筛选栏最左侧。
- 左侧标签面板改为暖黄色抽屉。
- 语音圆形按钮留空。
- 新建按钮缩小。
- 编辑页增加标签输入框。

## 7. 验收方式

1. 启动 App，确认启动页只显示圆环 logo、`小泓便签`、`为记录而生，也为效率而来`。
2. 进入首页，确认顶部显示小圆环 logo、`小泓便签`、`语音智能的便签 App`。
3. 确认设置入口是齿轮按钮，不再是“设置”文字按钮。
4. 确认标签入口位于搜索框下方，且在 `全部 / 待办 / 已完成 / 置顶` 左侧。
5. 点击标签入口，确认左侧抽屉从左边出现，高度占满屏幕，宽度不占满屏幕，背景为暖黄色系。
6. 新建便签，填写标签，例如 `客户、硬件`，保存后确认卡片只显示该便签的标签。
7. 编辑待办便签为普通便签，确认原标签不会被改成“普通”，其他便签的标签也不会被影响。
8. 确认语音按钮为空圆形按钮，新建按钮不是全宽大按钮。

## 8. 已知限制

- 本阶段仍然没有做完整 Tag CRUD。
- 标签暂时以 `notes.tag_text` 轻量保存，Phase1-03 再接入正式 `tags` 和 `note_tag_cross_ref`。
- 搜索框仍是 UI 占位，真实搜索放在 Phase1-04。
- 语音按钮仍是 UI 占位，不接语音逻辑。

## 9. 下一阶段建议

继续留在 Phase1-01/Phase1-02 之间先稳定现有体验。确认本次 UI 和标签修正没有问题后，再进入 Phase1-02：删除、置顶、待办完成状态。
