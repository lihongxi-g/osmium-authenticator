# Osmium v2.3.9（紧急修复版 / Emergency fix）

**发布日期 / Release date: 2026-09-05**

> 版本号与 versionCode 与 v2.3.9 正式版一致（v2.3.9 / 46），APK 文件名带
> `-emergency-fix1` 后缀用于区分。若已安装 v2.3.9，直接覆盖安装即可。

## 中文更新说明

本版为 v2.3.9 的紧急修复，无新功能，包含以下修复：

- 修复「随机排序」模式下，应用启动后新添加（或导入）的账户不会出现在列表中、必须彻底退出重进才能看到的问题；现在新账户会立即追加到列表末尾，启动时的原有排序保持不变。
- 手动添加或编辑 HOTP 账户时，现在可以直接设置起始计数器。此前新账户只能从 0 开始、且详情页只能每次 +1，迁移计数器已前进的存量账户几乎不可行。
- 从账户详情页复制验证码现在会计入「按复制次数排序」的统计，与主页卡片行为一致。
- 局域网快捷传输服务器加固：对端连接增加 15 秒读取超时；只有失败的交换才消耗尝试次数，成功的传输或无关的杂散连接不再导致发送端服务器被强制关闭；相关错误提示已本地化。
- 自动备份的调度计算改用 java.time，正确处理夏令时切换日的跳变与重复时刻（对不使用夏令时的时区无行为变化）。

## English release notes

This is an emergency-fix build of v2.3.9 — the versionCode stays 46 and the
APK filenames carry the `-emergency-fix1` suffix to tell it apart from the
original v2.3.9 release assets. Install over an existing v2.3.9 directly.

- Fixed accounts added or imported after launch being invisible under the
  "shuffle once per launch" sort mode until the app was fully restarted. New
  accounts now appear immediately at the end of the list without disturbing
  the launch-time order.
- Manually adding or editing a HOTP account now lets you set the starting
  counter directly. Previously every new HOTP account started at 0 with no
  way to jump ahead, making migration of existing mid-sequence accounts
  impractical.
- Copying a code from the account detail screen now counts toward the
  "sort by copies" ordering, matching the home-screen cards.
- LAN transfer server hardening: peer sockets now have a 15 s read timeout,
  only failed exchanges consume the attempt budget (successful transfers or
  stray connections no longer shut the sender down), and the shutdown notice
  is localised.
- Auto-backup scheduling now computes next-run times with java.time and
  handles DST transitions correctly (no behaviour change for zones without
  DST).
