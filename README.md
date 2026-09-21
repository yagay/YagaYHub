# YagaYHub

YagaYHub 是 YagaY Android 项目的统一入口中心。打开后可以查看自己的项目、安装状态和版本，并直接启动已经安装且带 Launcher Activity 的 App。

## 1.5.0

- 每个已登记 GitHub 仓库的 App 条目新增带运行图标的 `Actions` 一键入口。
- 直接显示最近一次 Actions 状态：成功、失败、运行中、排队中、已取消、无记录或未知。
- 只扫描每个仓库最新一次 Actions，不再向前查找更早的成功构建。
- 只有最新一次 Actions 状态为成功时，才继续读取该 run 的未过期 artifact ZIP；失败、运行中、排队中、取消等状态都不会抓取 ZIP。
- 下载图标仅在最新成功构建存在可用 artifact 时高亮；配置 Token 后点击会直接把 ZIP 保存到 `Downloads/YagaYHub/`，不经过 GitHub 网页。
- 显示 artifact ZIP 大小（KB / MB / GB）；仅当最新一次 Actions 成功且存在未过期 artifact 时读取和显示。
- 顶部新增“设置”入口，可在手机端粘贴 GitHub Fine-grained Token；Token 使用 Android Keystore + AES/GCM 加密，仅保存于本机，不写入仓库源码。
- 状态和 artifact API 请求会自动使用已保存 Token；直接下载使用 GitHub artifact API，并通过 MediaStore 写入系统 Downloads。
- 点击 `Actions` 直接打开对应仓库的 GitHub Actions 页面，便于查看或触发工作流。
- 自动发现但尚未登记 GitHub 仓库的 App 不显示 `Actions` 入口。

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

### GitHub Token 使用

1. 安装或升级 YagaYHub 1.5.0。
2. 打开 YagaYHub，点击顶部“设置”。
3. 粘贴 Fine-grained GitHub Token 并点击“保存”。
4. 返回列表；最新一次 Actions 成功且存在 artifact 时会显示 ZIP 大小和高亮下载图标。
5. 点击下载图标，ZIP 会直接保存到 `Downloads/YagaYHub/`。

Token 至少需要对应仓库的 Actions 只读权限。不要把 Token 提交到 GitHub 源码或聊天内容中。

## Package

`com.yagay.YagaYHub`

## Build

本项目与现有 YagaY Android 项目保持一致，使用 JDK 17、AGP 9.2.0 和 Gradle 9.4.1。

```bash
gradle assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

仓库内的 GitHub Actions 会在 push / pull request 时自动使用 Gradle 9.4.1 构建 Debug APK，因此当前不依赖 Gradle Wrapper。

## 1.3.0 YBrowser 集成

- 浏览器从 YagaYHub 中完全拆分，独立维护在 `yagay/YBrowser`。
- YagaYHub 不再包含 WebView、GeckoView 或 BrowserActivity。
- YagaYHub 内部 GitHub / 网页链接通过 YBrowser 的公开 Intent API 打开。
- Package: `com.yagay.YBrowser`
- Action: `com.yagay.YBrowser.action.OPEN_URL`
- Extra: `com.yagay.YBrowser.extra.URL`
- 未安装 YBrowser 时会提示先安装 YBrowser。
- YBrowser 作为独立项目负责双内核、标签页、浏览器 UI 和后续浏览器功能。
