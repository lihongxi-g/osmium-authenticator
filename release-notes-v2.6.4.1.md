# Osmium v2.6.4.1

**发布日期 / Release date: 待定 (TBD)** · versionCode 901–903（901 = armeabi-v7a、902 = arm64-v8a、903 = x86_64）

这是一个修复更新检查的小版本，同时加了每个版本首次启动时的欢迎页。

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

## APK SHA-256

```
待 CI 构建后填入
```
