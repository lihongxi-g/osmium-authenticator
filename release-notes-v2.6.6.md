# Osmium v2.6.6

**2026-11-20** · versionCode 956 · 正式版 · 通用包（arm64-v8a、armeabi-v7a、x86_64）

## 更新内容

- 新增:设置 → 安全 → 验证设置。可逐个选择哪些操作仍需先验证身份:导出、明文导出、导入、局域网传输、WebDAV、自动备份。进入本页必须先通过验证,检测到 root 时本页不可用且所有验证强制恢复;开发者模式始终需要验证。
- 新增:外观 → 预测式返回动画(默认开启)。返回手势进行中页面跟随手指移动,下面那一页同时滑入;关闭后返回照常工作,只是不再预览。
- 新增:关于 → 关注 Osmium 增加 F-Droid 图标,点击打开 F-Droid 页面;来源说明中补充该图标的署名与双许可。
- 变更:打开时验证、允许截屏、隐藏验证码三项仍固定需要验证,不列入验证设置。
- 修复:此前侧滑返回会露出空白底色的页面(浅色主题下是白屏),现在真实的上一页会跟着滑入;滑动完成后的跳转不再重复播放一次动画。
- 变更:隐私政策与用户协议同步更新(10 种语言),生效日期为 2026-11-20。

## Changes

- New: Settings → Security → Verification settings chooses which actions still ask for your identity first: export, plaintext export, import, LAN transfer, WebDAV and auto backup. Opening the page always requires verification, it is unavailable once root is detected (every check is then forced back on), and developer mode always verifies.
- New: Appearance → Predictive back animation (on by default). While the back gesture runs the current screen follows your finger and the screen underneath slides in; with it off back still works, it just no longer previews.
- New: About → Follow Osmium gained an F-Droid icon that opens the F-Droid page; the attributions page now names the mark's author and both of its licenses.
- Changed: verify on open, screenshot blocking and hidden codes stay permanently verified and are not part of the verification settings.
- Fixed: swipe-back no longer reveals an empty background (white in the light theme) — the real previous screen slides in, and the pop that follows the gesture no longer replays a second animation.
- Changed: Privacy Policy and Terms of Use updated in all 10 languages; effective date 2026-11-20.

签名证书 SHA-256:`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
