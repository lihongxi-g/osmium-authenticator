# Osmium v2.5.3

**发布日期 / Release date: 2026-09-23**

## 中文更新说明

这版主要是修 bug，加上几处用着别扭的地方：

- 修好了局域网传输的配对码输入：以前打第二位数字会跑到第一位前面，12 位码打出来是乱的，现在按顺序输入不会再跳。
- 从其他验证器导入改成直接选备份文件：进去就弹系统文件选择器，Osmium 自己判断是 Aegis、2FAS 还是 Raivo OTP 的备份，识别结果显示在预览上方，不用先选来源。
- 开发者模式里隐藏「Android 完整性检测」之后，启动时真的不再检测了（之前只是把设置里的入口藏起来，后台还在跑）。
- 检查更新会显示更新内容：有新版本时弹窗里直接能看到这一版改了什么；关于页的「检查更新」也改成真去查一次，已是最新 / 查询失败都会提示。
- 关于页「关注 Osmium」加了个网站图标，点进 osmium.im。

另外这版还带上了前面几周做的改动：局域网传输换成新协议（接收方出码、发送方输入，配对码 6 位改 12 位，一次性 ECDH 会话密钥加双向验证，负载 AES-256-GCM 加密）、局域网风险提醒与 24 小时拉黑、没有指纹的设备打开应用会提醒设 PIN、Android 16 KB 页大小适配（AGP 8.5.2 / Gradle 8.7）。

安装：versionCode 64（642 = arm64-v8a、641 = armeabi-v7a、643 = x86_64），覆盖安装即可，账户和设置原样保留。签名指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

注意：局域网传输的新协议和旧版不通用，两台设备都要升到 v2.5.3 才能互传；旧版本之间照旧可以互传。

## English release notes

Mostly bug fixes, plus a few rough edges:

- LAN transfer pairing code: digits used to land in front of the ones already typed, so a 12-digit code came out scrambled. Typing it in order now works.
- Importing from another authenticator: pick the backup file directly. Osmium detects on its own whether it is an Aegis, 2FAS or Raivo OTP export and shows what it found above the preview.
- Hiding "Android integrity detection" in developer mode now really stops the check at launch (before, it only hid the settings entry while the check kept running).
- Update checks show what changed: the dialog lists the new release's notes, and About → check for updates actually queries instead of jumping straight to the browser.
- About → Follow Osmium has a website icon that opens osmium.im.

This release also carries the work from the previous weeks: the new LAN transfer protocol (the receiver shows the code, the sender types it; 12-digit code; one-off ECDH session key with mutual authentication, payload sealed with AES-256-GCM), local-network risk warnings with a 24-hour block list, a PIN reminder on devices without biometrics, and 16 KB page-size support (AGP 8.5.2 / Gradle 8.7).

Install: versionCode 64 (642 = arm64-v8a, 641 = armeabi-v7a, 643 = x86_64); it installs over v2.5.2 and keeps your accounts and settings. Same signing certificate as always, SHA-256 fingerprint `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.

Note: the new LAN transfer protocol is not compatible with older builds — both devices need v2.5.3 to transfer to each other. Older versions still work with each other.
