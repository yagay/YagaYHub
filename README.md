# YagaYHub

YagaYHub 是 YagaY Android 项目的统一入口中心。打开后可以查看自己的项目、安装状态和版本，并直接启动已经安装且带 Launcher Activity 的 App。

## v1.0.0

- 显示已知项目，即使尚未安装也能看到。
- 已安装项目自动读取真实应用名称、图标和版本号。
- 点击“打开”直接进入对应 App。
- 没有 Launcher Activity 的 LSPosed / 模块类项目会明确显示“无直接入口”。
- 每个已知项目可直接跳转 GitHub。
- 支持名称、包名、功能描述搜索。
- 支持“全部 / 已安装 / 未安装”筛选。
- 自动发现额外的 `com.yagay.*` Launcher App。
- 已登记 FloatLens、List Cleaner、MiniWindowGuard、AIHub、TaskManagerX、Dual Signal、ChromeX、NFCExpertPro、GboardHook、NoOverlayWarning、上班总时间和 Legado MD3。
- 不申请 `QUERY_ALL_PACKAGES`。

## Package

`com.yagay.YagaYHub`

## Build

本项目与现有 YagaY Android 项目保持一致，使用 JDK 17、AGP 9.2.0 和 Gradle 9.4.1。

```bash
gradle assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

仓库内的 GitHub Actions 会在 push / pull request 时自动使用 Gradle 9.4.1 构建 Debug APK，因此当前不依赖 Gradle Wrapper。
