# Osmium v2.6.1

**发布日期 / Release date: 2026-10-28**

本次更新把版本检查整理成独立页面，并新增可选的「预览版」更新通道。此外同步更新了隐私政策与用户协议。

## 中文更新说明

### 新增：更新检查独立页面，正式版与预览版分开开关

- 设置 → 关于 → 「检查更新」现在进入独立页面，里面有两个开关：**检查正式版更新**、**检查预览版更新**。
- 两个开关各自管一个通道，互不依赖：只开正式版 = 与之前行为完全一致；只开预览版 = 只接收开发者在 GitHub 上标记为 pre-release 的版本；两个都开 = 取两者中版本更高的那个；两个都关 = 应用完全不发起版本检查请求。
- **预览版开关默认关闭**：预览版是测试版本，不会主动推给任何人，需要你自己打开。
- 「关于」页面里的手动检查同样遵守你的选择：手动检查总会查询正式版，开启预览版开关时会一并考虑预览版。
- 版本比较现在能正确区分预览版与正式版：安装 2.6.1-beta 的用户会收到 2.6.1 正式版的更新提示。此前两者被判为同一版本，装了 beta 的人等不到同版本正式版的推送。

### 其他

- 新增 6 条界面文案，11 种语言同步。
- **隐私政策与用户协议同步更新**（生效日期 2026 年 10 月 28 日，适用于 v2.6.1 及后续版本）：说明两个更新通道各自的行为、预览版的测试性质，以及两者都关闭时应用不发起任何版本检查请求。
- 无新增权限、无第三方 SDK、无数据收集。

## English release notes

This release turns the version check into its own settings page and adds an optional preview channel. The Privacy Policy and the Terms of Use were updated to match.

### New: a dedicated update-checks page with separate stable and preview switches

- Settings → About → "Update checks" now opens its own page with two switches: **Check for stable updates** and **Check for preview builds**.
- Each switch covers one channel and neither depends on the other: stable only is exactly the previous behaviour; preview only follows the releases the developer marks as pre-release on GitHub; both pick the higher version of the two; with both off the app makes no version-check request at all.
- **The preview switch is off by default**: a preview is a test build, and it never arrives unless you ask for it.
- The manual check in About respects the same choice: it always queries the stable channel and includes preview builds while that switch is on.
- Version comparison now ranks a pre-release below the released build of the same number, so a user testing 2.6.1-beta is offered the final 2.6.1. The old numeric-only compare treated both as the same version and never offered the release.

### Other

- 6 new UI strings, added in all 11 languages.
- **Privacy Policy and Terms of Use updated** (effective October 28, 2026; applies to Osmium v2.6.1 and later): they describe each channel, the test nature of preview builds, and that no version-check request is made while both switches are off.
- No new permissions, no third-party SDKs, no data collection.

## APK 校验和 / Checksums

```
a35fe5fb6d45dcda211b76d906c7cc6b121fd1bee8f5ff72ef1e8f0b762a7749  osmium-2.6.1-arm64-v8a.apk
6e7f5e28375cb866efdec40f76a8e21b2a258223e0e15f080a502781a2baa4f1  osmium-2.6.1-armeabi-v7a.apk
204fdb67e33b63c538bf46d0fb1211210e17b22e1085949f51bb3a1abb71b747  osmium-2.6.1-x86_64.apk
```

同一份校验和也在 Release 页面的 `SHA256SUMS.txt` 中（`sha256sum -c SHA256SUMS.txt` 可直接校验）。/ The same checksums are in `SHA256SUMS.txt` on the release page (`sha256sum -c SHA256SUMS.txt`).

## 安装 / Install

versionName **2.6.1**，versionCode **79**（791 = armeabi-v7a、792 = arm64-v8a、793 = x86_64）。覆盖安装即可，账户与设置原样保留。

签名证书指纹不变（SHA-256）：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

versionCode 79 (791 = armeabi-v7a, 792 = arm64-v8a, 793 = x86_64). Installs over the previous version and keeps your accounts and settings. Unchanged signing certificate, SHA-256 `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
