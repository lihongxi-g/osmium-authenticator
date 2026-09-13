# Osmium v2.4.4

**发布日期 / Release date: 2026-09-13**

## 中文更新说明

v2.4.4 修复了冷启动时账号列表短暂闪现“还没有账户”的问题。

### 修复

- **冷启动不再闪现“还没有账户”**：打开应用时，账号数据需要先从本地加密数据库读取并逐个解密，随后才能显示列表。在这个短暂的加载窗口内，界面此前会误显示“还没有账户”空态（约零点几秒）。现在改为显示“加载中…”（小转圈），数据就绪后自动切换为账户列表；只有确认没有任何账户时才显示“还没有账户”。
  - 开启“打开时验证”的设备不受影响（验证期间加载已完成），行为与之前一致。
- 版本号 **2.4.4（versionCode 59）**，可覆盖安装 v2.4.3 及更早版本。

### 说明

- 本次仅修改界面显示逻辑：加密、存储、备份机制均未改动，账户数据与全部设置原样保留。
- 与以往版本使用同一签名证书。

## English release notes

v2.4.4 fixes the brief "no accounts yet" flash shown on cold start.

### Fixed

- **No more empty-state flash on cold start**: on launch, account data must first be read from the local encrypted database and decrypted field by field before the list can render. During that short window the screen used to misreport "no accounts yet" for a fraction of a second. It now shows a "Loading…" placeholder (small spinner) and switches to the account list as soon as the data is ready; the empty state appears only once the vault is confirmed empty.
  - Devices with "verify on open" enabled were unaffected (loading already finishes behind the gate); their behavior is unchanged.
- Version **2.4.4 (versionCode 59)** — installs over v2.4.3 and earlier versions.

### Notes

- UI display logic only: encryption, storage and backup are untouched; account data and all settings are preserved.
- Signed with the same certificate as previous releases.
