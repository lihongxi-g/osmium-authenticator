# Osmium v2.5.4

**发布日期 / Release date: 2026-09-24**

## 中文更新说明

这版只修两个问题，都是 F-Droid 的测试员在真机上测出来的：

- 修好了「打开时验证」的 fail-open：开了这个开关又没设 app PIN 时，只要指纹**暂时**用不了（传感器忙、连续按错被临时锁定），冷启动就能直接进主界面看到验证码。现在这种状态一律保持锁定——可以用设备密码或 app PIN 解锁，指纹恢复后会自动弹验证。真没有生物识别的设备行为不变：还是打开时提醒你设一个 PIN。
- 修好了粘贴被截断的 Google 迁移链接会崩溃的问题：这类畸形数据现在只提示「无法解析」，不再把进程杀掉。

安装：versionCode 65（652 = arm64-v8a、651 = armeabi-v7a、653 = x86_64），覆盖安装即可，账户和设置原样保留。签名指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

## English release notes

Two fixes, both found by the F-Droid reviewer on a real device:

- The "verify on open" gate could fail open: with no app PIN set and biometrics temporarily unusable (sensor busy, or a temporary lockout after failed attempts), a cold start showed the account list with live codes. That state now keeps the app locked — the lock screen offers your device password or the app PIN, and the biometric prompt returns on its own once the sensor recovers. Devices with no biometric credential keep the previous behaviour (the app only suggests setting a PIN).
- Pasting a truncated Google Authenticator migration link crashed the process (StringIndexOutOfBoundsException). Malformed payloads are now rejected with the normal "cannot parse" error.

Install: versionCode 65 (652 = arm64-v8a, 651 = armeabi-v7a, 653 = x86_64); it installs over v2.5.3 and keeps your accounts and settings. Same signing certificate as always, SHA-256 `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
