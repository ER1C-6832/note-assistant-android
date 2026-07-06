# Phase 0 后续步骤

## 1. 首次导入

```bash
git clone git@github.com:ER1C-6832/note-assistant-android.git
cd note-assistant-android
# 将 zip 内容复制到这里
```

用 Android Studio 打开 `note-assistant-android` 根目录。

## 2. Gradle Wrapper

本 zip 没有携带 wrapper jar。首次同步成功后生成 wrapper：

```bash
gradle wrapper --gradle-version 8.10.2
```

然后提交：

```bash
git add gradlew gradlew.bat gradle/wrapper
git commit -m "chore: add Gradle wrapper"
```

## 3. Phase 0 提交建议

```bash
git add .
git commit -m "chore: initialize Android phase 0 project"
git push origin main
```

## 4. Phase 0 检查点

- Gradle Sync 成功。
- `app` 能运行到首页。
- 首页显示 demo notes。
- 新建按钮能进入编辑页。
- 设置按钮能进入设置页。
- Room 数据库能创建 `note_assistant.db`。
- Hilt 没有编译错误。

## 5. 下一阶段入口

Phase 1 从 `notes-domain` 和 `notes-data` 开始，不要先碰语音：

1. 完成 CreateNoteUseCase / UpdateNoteUseCase / DeleteNotesUseCase。
2. 完成 NoteDao CRUD。
3. 将 NoteEditorScreen 从静态 UI 改为真实输入和保存。
4. 接入 TagEntity / TagDao / NoteTagCrossRef。
5. 添加搜索页和 LIKE 搜索。
