# Osmium v2.5.2（修复版 / Fix1）

**发布日期 / Release date: 2026-09-21**

> 这是 v2.5.2 的修复构建：版本号与 versionCode 跟 v2.5.2 正式版完全一致
> （2.5.2 / 62；621 = armeabi-v7a、622 = arm64-v8a、623 = x86_64）。
> APK 文件名带 `-fix1` 后缀用于区分；下载站继续提供不带后缀的文件名，
> 旧链接不受影响。已安装 v2.5.2 直接覆盖安装即可，账户数据与全部设置原样保留。

## 中文更新说明

本版只修一个问题：**在没有指纹/人脸、只能用 app PIN 验证的设备上，开启「打开时验证」后需要验证两次才能进入应用。**

- 现象：开启「打开时验证」并设置 app PIN 后打开应用，PIN 输对后会立刻再出现「Osmium 已锁定 / 验证身份后查看验证码」页面，必须再输一次 PIN（或改用系统密码）才能进入。
- 原因：PIN 验证通过后只解除了 PIN 门，没有同时清掉生物识别锁定标记，锁定页因此又叠了上来。该问题自 v1.7.0 加入「打开时验证」开关起就存在，只在没有可用生物识别的设备上可见——有指纹的手机走的是指纹通道，不受影响。
- 现在 PIN 输对即完成验证，一次进入。
- 这类设备上的锁定页不再显示点了只会提示「生物识别不可用」的解锁按钮，而是直接进入 PIN 输入界面。

### 说明

- 加密、存储、备份机制未改动，账户数据与全部设置原样保留。
- 与以往版本使用同一签名证书，SHA-256 指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。
- 有指纹/人脸且已录入的设备行为与 v2.5.2 正式版一致。

## English release notes

This is a fix build of v2.5.2 — the versionName and versionCode are identical to
the v2.5.2 release (2.5.2 / 62; 621 = armeabi-v7a, 622 = arm64-v8a, 623 =
x86_64). The APK filenames carry a `-fix1` suffix; the download site keeps
serving the unsuffixed names so existing links keep working. Install over an
existing v2.5.2 directly — account data and all settings are preserved.

- Fixed: on a device with no usable biometrics (no enrolled fingerprint/face),
  enabling "verify on open" asked for the app PIN **twice**. After the first
  correct PIN the "Osmium is locked" screen appeared again and a second
  verification (PIN or device credential) was required.
- Cause: a successful PIN cleared only the PIN gate, not the biometric lock
  flag, so the lock gate stacked on top of it. The bug has been present since
  the "verify on open" toggle was added in v1.7.0 and is only observable on
  devices without biometrics — phones with a fingerprint use the biometric
  path and were never affected.
- The lock gate on such devices now opens directly on the PIN pad instead of
  showing an "unlock" button whose biometric call can only report
  "biometric unavailable".
