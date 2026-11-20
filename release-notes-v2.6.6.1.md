# Osmium v2.6.6.1（预览版）

**2026-11-20** · versionCode 957 · 预览版（pre-release）· 通用包（arm64-v8a、armeabi-v7a、x86_64）

预览版只用来验证下面这一处改动，确认没问题后再进正式版。

## 更新内容

- 新增:开启「隐藏验证码」后,点一下验证码圆点会直接显示数字,再点一下即可复制。
- 变更:显示 6 秒后自动变回圆点,切换到后台立即变回。
- 变更:账户详情页同样支持点按显示,卡片下方的提示在隐藏状态下改为「点按显示」。
- 变更:「隐藏验证码」的设置说明同步改为新的操作方式(11 种语言)。

## Changes

- New: with "Hide codes" on, tapping a code reveals its digits in place; tapping again copies it.
- Changed: the digits go back behind dots after 6 seconds, and immediately when Osmium leaves the foreground.
- Changed: the account detail screen behaves the same way and its hint switches to "Tap to show" while the code is masked.
- Changed: the "Hide codes" setting description now describes the new behaviour in all 11 languages.

签名证书 SHA-256:`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
