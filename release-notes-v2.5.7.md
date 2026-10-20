# Osmium v2.5.7

**发布日期 / Release date: 2026-10-20**

## 中文更新说明

修复自动备份「点了立即备份却什么都没发生」的问题。

- 「立即备份」不再交给系统调度器排队：现在在应用内直接执行，按钮显示「备份中…」，结束后给出结果——成功显示保存的文件名（下载/Osmium 目录），失败显示具体原因，不再只弹一句「备份已开始」然后没有下文。
- 本地备份写入后会回读并校验文件长度：写坏了会明确报错，不会出现「显示成功但文件管理器里找不到文件」。
- 备份过程逐阶段写入应用日志（目标与保留数、跳过原因、数据大小、写入位置、清理数量、最终结果、异常栈），导出的日志能直接看出停在哪一步。
- 定时备份若因系统限制没有执行，打开应用时会检测到逾期并自动补跑一次。
- 新增 5 条界面文案，11 种语言同步。

安装：versionCode 70（702 = arm64-v8a、701 = armeabi-v7a、703 = x86_64），覆盖安装即可，账户和设置原样保留。签名指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

## English release notes

Fixed automatic backup where "Back up now" reported that it started and then nothing happened.

- "Back up now" no longer waits in the system scheduler queue: it runs inside the app, the button shows "Backing up…", and the result is reported — the saved file name (Download/Osmium) on success, the actual reason on failure, instead of a single "backup started" toast followed by silence.
- Local backups are read back and length-checked after writing, so a broken write fails loudly instead of claiming success while no file exists.
- Every stage of a backup is logged (target and retention, skip reason, payload size, written location, pruned count, final result, exception trace), so an exported log shows exactly where it stopped.
- If the system never ran a scheduled backup, opening the app detects the overdue schedule and runs one.
- Five new UI strings, added in all 11 languages.

Install: versionCode 70 (702 = arm64-v8a, 701 = armeabi-v7a, 703 = x86_64); it installs over v2.5.6 and keeps your accounts and settings. Same signing certificate as always, SHA-256 `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
