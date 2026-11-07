# Osmium v2.6.4.1

**发布日期 / Release date: 待定 (TBD)** · versionCode 911–913（911 = armeabi-v7a、912 = arm64-v8a、913 = x86_64）

这是一个修复更新检查的小版本，同时加了每个版本首次启动时的欢迎页，并把弹窗改成只能点按钮退出。

## 中文更新说明

### 修复：首次启动的更新检查被跳过

- 打开应用时会读设置里「检查正式版 / 检查预览版」两个开关；但冷启动时这段代码跑在设置从磁盘读出来之前，看到的是默认值（都关），于是直接放弃检查。
- 结果是：安装后第一次打开不检查更新，要等切到后台再回前台、第二次触发时才检查。现在会等真实设置到位再判断，第一次打开就正常检查。

### 修复：连不上 GitHub 时谎报「已是最新」

- 「关于 → 检查更新」在断网、被墙、或 GitHub 限流时会提示「当前已是最新版本」——把请求失败当成了没有新版本。
- 现在失败就如实提示「检查更新失败，请稍后再试」，「已是最新」只在真的查到版本号之后才出现。

### 新增：每个版本首次启动的欢迎页

- 第一次打开某个版本时弹一次：欢迎与致谢，说明验证码只在本机生成、没有账号/云端/追踪。
- 一屏直达官网、Telegram、GitHub、F-Droid 四个入口。
- 底部放两个更新通道开关（正式版 / 预览版），选完点「开始使用」即生效，之后仍可在「设置 → 更新检查」里改；不填也能改。
- 默认：正式版打开（你在这里点的「开始使用」就是同意），预览版关闭——测试包不该自己找上门。
- 每个版本只弹一次：弹过的版本号记在本机，升级到新版本会再弹一次。

### 变更：弹窗只能点按钮退出

- 全部 39 个对话框不再因为点到外面（或按返回键）而关闭——误触不会丢掉正在填的内容或正在确认的操作，必须点对话框上的按钮。
- 原本只能靠点外面关掉的 15 个对话框（选文件、选频率、根检测报告、密钥库状态、验证方式选择等）都补了一个「取消 / 关闭」按钮；本来就有关闭按钮的保持原样。

### 变更：F-Droid 入口用官方图标

- 欢迎页的 F-Droid 行改用 F-Droid 项目官方的机器人标识（`drawable-nodpi/fdroid_logo.png`），不再是通用的 Android 机器人。
- 关于 → 开源致谢里补了这张图标的来源与许可（GPL-3.0-or-later），并注明 Osmium 与 F-Droid 项目没有隶属关系。

## English notes

### Fixed: the first launch skipped the update check

- The check reads the two channel switches from settings, but on a cold start it ran before those settings had been loaded from disk and saw the defaults (both off), so it gave up.
- A fresh install therefore never checked on the first open — only after going to the background and back. It now waits for the stored settings, so the first open checks normally.

### Fixed: a failed check claimed you were up to date

- About → check for updates answered "you already have the latest version" when offline, blocked, or rate limited: a failed request was treated as "no newer release".
- A failure now says the check failed; "up to date" only appears after a version was actually fetched.

### New: a welcome page on the first start of each version

- Shown once per version: a short welcome, plus the fact that codes are generated on the device — no account, no cloud, no tracking.
- Direct links to the website, Telegram, GitHub and F-Droid.
- Both update-channel switches (stable / preview) sit at the bottom; "get started" applies them, and Settings → Update checks can change them later.
- Defaults: stable on (tapping "get started" is the consent), preview off — a test build must never arrive uninvited.
- Once per version: the shown version is recorded locally, so upgrading to a new version shows it again.

### Changed: dialogs only close from their buttons

- All 39 dialogs now ignore taps outside them (and the back gesture), so a mistimed tap no longer throws away what was being entered or confirmed - the buttons are the only way out.
- The 15 dialogs that previously relied on an outside tap (file picker, frequency picker, root report, keystore status, verification chooser, ...) each got an explicit cancel/close button; the ones that already had one are unchanged.

### Changed: the real F-Droid mark

- The F-Droid row on the welcome screen now uses F-Droid's own robot mark (`drawable-nodpi/fdroid_logo.png`) instead of a generic Android robot.
- About → attributions gained the source and licence of that artwork (GPL-3.0-or-later), noting that Osmium is not affiliated with or endorsed by the F-Droid project.

## APK SHA-256

```
待 CI 构建后填入
```
