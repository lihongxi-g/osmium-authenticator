# Osmium v2.6.6.2

versionCode 962 · 通用 APK / Universal APK (arm64-v8a, armeabi-v7a, x86_64)

## 更新内容

- 修复关闭「打开时验证」后，启动时误闪锁定页；仍保留需要的身份验证。
- 缩小首页和设置子页的顶部留白，让列表滚动延伸到屏幕边缘；保留系统状态栏和导航栏。
- 窄屏或文字较长时，权限、完整性、自动备份和局域网传输的卡片可换行显示。
- 合入 v2.6.6.1 的预览功能：点圆点显示隐藏的验证码，再点一次复制；6 秒后或切到后台时重新隐藏。

## Changes

- Fixed the brief lock screen shown on startup with “Verify on open” disabled, without removing required verification.
- Reduced top spacing and extended scrolling to the screen edges on the account list and settings subpages, while keeping the system bars.
- Long labels can wrap in the permissions, integrity, automatic backup, and LAN transfer cards on narrower screens.
- Included the v2.6.6.1 preview feature: tap the dots to reveal a hidden code, then tap again to copy. Codes hide again after six seconds or on leaving the foreground.

APK: `osmium-2.6.6.2.apk` · SHA-256: see the attached `SHA256SUMS.txt`.

Signing certificate SHA-256: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
