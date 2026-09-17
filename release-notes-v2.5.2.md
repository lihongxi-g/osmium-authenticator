# Osmium v2.5.2

**发布日期 / Release date: 2026-09-17**

## 中文更新说明

v2.5.2 是一个维护版本：**只修正商店页（F-Droid 等）展示的应用图标**，让它与桌面图标一致（白底 + 金属蓝灰盾牌）。此前仓库里的商店图标是旧版「O」字标的遗留，装上应用后看到的却是盾牌图标，两者不一致。

应用本身与 v2.5.1 完全相同，没有任何功能变化。

### 变更

- 商店图标签改为从启动图标的矢量资源（自适应图标）直接生成，512×512。
- 版本号 **2.5.2**（621 = armeabi-v7a、622 = arm64-v8a、623 = x86_64），可覆盖安装 v2.5.1 及更早版本。

### 说明

- 加密、存储、备份机制未改动，账户数据与全部设置原样保留。
- 与以往版本使用同一签名证书，SHA-256 指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。
- 安装包仍可从公开源码逐字节复现构建。

## English release notes

v2.5.2 is a maintenance release: it **only fixes the icon shown on store listings** (F-Droid and the fastlane metadata) so that it matches the launcher icon (white background with the blue-grey shield). The previous store icon was a leftover of the older "O" mark, which did not match what users see after installing.

The app itself is identical to v2.5.1, with no functional changes.

### Changed

- The store icon is now rendered directly from the adaptive launcher icon resources, at 512x512.
- Version **2.5.2** (621 = armeabi-v7a, 622 = arm64-v8a, 623 = x86_64) — installs over v2.5.1 and earlier.

### Notes

- Encryption, storage and backup are untouched; account data and all settings are preserved.
- Signed with the same certificate as previous releases; SHA-256 fingerprint unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
- Release APKs can still be rebuilt byte-for-byte from the public source.
