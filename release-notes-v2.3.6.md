# Osmium v2.3.6 / Osmium v2.3.6

**发布日期 / Release date: 2026-08-22**

## 中文更新说明

### 新增：标签分类

- 支持创建、重命名、修改颜色和删除本地标签。
- 一个账户可以同时绑定多个标签。
- 账户列表支持“全部”“未分类”和多标签筛选。
- 多个标签同时选择时采用“任意标签匹配”规则。
- 搜索与标签筛选可以同时使用。
- 账户卡片显示紧凑标签提示，过多标签以 `+N` 表示。
- 标签数据会随现有加密备份保存。
- 恢复备份时，同名标签自动合并，不会覆盖本地标签颜色。
- 删除标签只会解除账户关联，不会删除账户。

### 兼容性与安全

- 保持 `versionName 2.3.6`、`versionCode 42`。
- 旧版本数据库会自动迁移，现有账户不会丢失。
- 没有新增网络服务、账号系统或遥测。
- 本版本改用 GNU GPL v3 或更高版本（GPL-3.0-or-later）开源。

## English release notes

### New: account tags

- Create, rename, recolor and delete local tags.
- Assign multiple tags to one account.
- Filter the account list by All, Uncategorized, or one or more tags.
- Multiple selected tags use OR semantics: an account matches any selected tag.
- Search and tag filters can be used together.
- Account cards show compact tag indicators, with `+N` for additional tags.
- Tag metadata and account relationships are included in the existing encrypted backups.
- During restore, tags with the same name are merged without replacing the local tag color.
- Deleting a tag only removes its account relationships; accounts are never deleted.

### Compatibility and security

- Keeps `versionName 2.3.6` and `versionCode 42`.
- Existing databases migrate automatically without losing accounts.
- No new network service, account system, or telemetry was added.
- This release is licensed under the GNU General Public License v3 or later (GPL-3.0-or-later).

## Assets / 构建包

- `osmium-2.3.6-arm64-v8a.apk` — modern Android phones / 现代手机（推荐）
- `osmium-2.3.6-armeabi-v7a.apk` — older 32-bit phones / 老款 32 位手机
- `osmium-2.3.6-x86_64.apk` — Android emulators / Android 模拟器

Signing certificate SHA-256 / 签名证书 SHA-256：
`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
