# Osmium v2.3.8

**发布日期 / Release date: 2026-08-23**

## 中文更新说明

### 修复：导入/恢复备份闪退与失败

- 修复备份导入（文件导入与 WebDAV 恢复）时，备份中的标签名称不合法（空白、超过 20 个字符）会触发未捕获异常导致应用闪退、且导入流程卡住无法继续的问题。
- 标签合并改为容错模式：异常的标签会被跳过并记录日志，其余账户与标签正常导入，导入预览必定出现，不再卡死。
- 导入预览改用数据库全新快照计算合并计划，避免连续导入时旧数据未刷新导致账户重复导入。
- 备份文件加载上限统一为 64 MiB（与 WebDAV 下载上限一致），误选超大文件时提示格式错误而不是内存溢出闪退。
- 导入文件的 PIN 校验数据损坏时按“PIN 错误”提示处理，不再崩溃。
- 导入写入数据库失败时给出错误提示，不再闪退。

### 版本说明

- **v2.3.7 已弃用（Deprecated）**：该版本号从未正式发布，其开发中的功能（Osmium Link 局域网查看）并入后续版本继续打磨；为避免版本号混乱，2.3.7 被跳过，本版本直接采用 **2.3.8**。请勿再以 2.3.7 作为发布版本号。
- `versionName 2.3.8`、`versionCode 45`。
- 兼容 2.3.6 及更早版本的数据库与备份格式（含标签）。

## English release notes

### Fixed: backup import/restore crash and failure

- Fixed a crash on backup import (file import and WebDAV restore): tags in a backup with invalid names (blank, or longer than 20 characters) triggered an uncaught exception that crashed the app and left the import flow stuck.
- Tag merging is now fault-tolerant: problematic tags are skipped and logged, the remaining accounts and tags import normally, and the preview always appears — it can no longer hang.
- The import preview now computes its merge plan from a fresh database snapshot, so consecutive imports no longer risk duplicate accounts from stale in-memory data.
- Backup payloads are capped at 64 MiB (matching the WebDAV download limit); picking an oversized file shows a format error instead of an out-of-memory crash.
- Corrupt PIN data inside an import file is treated as a wrong PIN instead of crashing.
- Database write failures during import now show an error toast instead of crashing.

### Version notes

- **v2.3.7 is deprecated**: that version number was never released; its work-in-progress feature (Osmium Link LAN account viewing) continues in later versions. To avoid version-number confusion, 2.3.7 is skipped and this release uses **2.3.8** directly. Do not use 2.3.7 as a release version again.
- `versionName 2.3.8`, `versionCode 45`.
- Compatible with databases and backup files (including tags) from 2.3.6 and earlier.

## Assets / 构建包

- `osmium-2.3.8-arm64-v8a.apk` — modern Android phones / 现代手机（推荐）
- `osmium-2.3.8-armeabi-v7a.apk` — older 32-bit phones / 老款 32 位手机
- `osmium-2.3.8-x86_64.apk` — Android emulators / Android 模拟器

Signing certificate SHA-256 / 签名证书 SHA-256：
`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
