# Phase1-01 资源编译修复报告

## 本阶段目标

修复 Phase1-01 UI 与标签修复包引入的 Android 资源链接错误，让工程恢复可构建状态。

## 问题现象

构建在 `:app:processDebugResources` 失败，AAPT 报错：

```text
style attribute 'android:attr/postSplashScreenTheme' not found
```

## 原因

`app/src/main/res/values-v31/styles.xml` 中误用了：

```xml
<item name="android:postSplashScreenTheme">@style/Theme.NoteAssistant</item>
```

`postSplashScreenTheme` 不是 Android framework 的 `android:` 命名空间属性。当前项目没有引入 AndroidX SplashScreen 主题体系，也不需要该属性；Activity 已经直接使用 `Theme.NoteAssistant`。

## 修改文件

- `app/src/main/res/values-v31/styles.xml`

## 修改内容

删除错误的：

```xml
<item name="android:postSplashScreenTheme">@style/Theme.NoteAssistant</item>
```

保留 Android 12+ 系统启动页相关的背景和图标属性：

```xml
<item name="android:windowSplashScreenBackground">#F8F3EA</item>
<item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
```

## 验收方式

```bat
gradlew.bat --stop
gradlew.bat clean :app:assembleDebug --no-build-cache
```

如果 Windows 仍提示 `classes.jar` 被占用，先关闭 Android Studio 的正在运行构建，执行 `gradlew.bat --stop`，必要时关闭 Android Studio 后删除对应模块的 `build/` 目录再重新构建。

## 未做内容

本次只修资源编译错误，不推进 Phase1-02，不新增功能。
