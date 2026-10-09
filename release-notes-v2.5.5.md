# Osmium v2.5.5

**发布日期 / Release date: 2026-10-09**

## 中文更新说明

这版改的是两处行为，都来自 F-Droid 的评审意见：

- **启动时不再联网。** 更新检查默认关闭（需要的话在设置里自己打开）；服务条款和隐私政策只在你打开「关于 → 相关文档」时才去拉取。全新安装冷启动不发任何网络请求。应用简介也按这个口径改写了：写明启动不联网，并列出两个可选的联网功能。
- **不是官方签名的包不再被硬性拒绝。** 之前检测到非官方签名就直接不让用，这会把 GPL 源码自建的包和 F-Droid 自己签名的包都挡在外面。现在改成提示 +「仍然继续」按钮，确认后可以正常使用（这个确认不会记住，每次冷启动会再提示一次）。

其它：11 种语言的文案同步更新。

安装：versionCode 66（662 = arm64-v8a、661 = armeabi-v7a、663 = x86_64），覆盖安装即可，账户和设置原样保留。签名指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

## English release notes

Two behaviour changes, both from the F-Droid review:

- **No network access at start-up.** The update check is now off by default (opt-in in Settings), and the Terms of Use / Privacy Policy are fetched only when you open About → the document. A fresh install makes no request on a cold start. The app description now says this explicitly and names the two optional network features.
- **Foreign-signed builds are no longer hard-blocked.** The app used to refuse to run unless it was signed with the official key, which also blocked GPL rebuilds and F-Droid's own signed builds. It now shows a warning with a *Continue anyway* button; the acknowledgement is not persisted, so the notice reappears on every cold start.

Also: strings in 11 locales updated.

Install: versionCode 66 (662 = arm64-v8a, 661 = armeabi-v7a, 663 = x86_64); it installs over v2.5.4 and keeps your accounts and settings. Same signing certificate as always, SHA-256 `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
