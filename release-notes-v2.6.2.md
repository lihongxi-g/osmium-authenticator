# Osmium v2.6.2

**发布日期 / Release date: 2026-11-XX**

本次更新新增「字体大小」设置，并修掉了两处大字号下会裁切文字的版式问题。

## 中文更新说明

### 新增：字体大小，四档可选

- 设置 → 外观 → **字体大小**：小 / 标准 / 大 / 特大四档，点选立即生效，不需要重启。
- 档位是在**系统字号之上**再放大：系统设置里已经把字调大的用户，仍然可以再选一档；「标准」等于完全跟随系统。
- 验证码和界面文字一起变化，图标、间距、卡片宽度不变。
- 页面顶部有实时预览：验证码样例、账号名会按当前档位显示，选之前就能看到效果。
- 上限做了封顶：应用档位与系统字号的合计放大不超过 2.0 倍，保证 PIN 键盘（固定 72dp 圆键）和账号卡片不会被撑破。

### 修复

- **账号卡片在系统大字号下裁切文字**：卡片原本是固定 96dp 高，标题 + 副标题 + 标签行在系统字号 130% 左右就会顶到边。现在改成最小高度 96dp，内容多了卡片自己长高；默认字号下外观与之前完全一致。
- **详情页验证码在超大字号下被截断**：验证码现在按可用宽度自适应，8 位码（HOTP / 8 位 TOTP）在最大档位下也不会被切掉。

### 其他

- 新增 7 条界面文案，11 种语言同步。
- 无新增权限、无第三方 SDK、无数据收集。

## English release notes

This release adds a text-size setting and fixes two layouts that clipped text at large font sizes.

### New: text size, four steps

- Settings → Appearance → **Text size**: Small / Default / Large / Largest. Tapping a step applies it immediately, no restart.
- A step multiplies **on top of** the system font scale, so users who already enlarged text system-wide can go one step further; "Default" follows the system exactly.
- Codes and interface text grow together; icons, spacing and card width stay put.
- The page has a live preview — a sample code and account name render at the selected step before you commit to it.
- The combined scale is capped at 2.0× so the PIN keypad (fixed 72dp keys) and the account cards cannot be pushed past what they can hold.

### Fixes

- **Account cards clipped their text at large system font sizes**: the card was a fixed 96dp tall, and title + subtitle + tag row ran out of room at around 130%. It is now a 96dp minimum, so the card grows with its content; at the default size the card looks exactly as before.
- **The detail-page code was cut off at very large sizes**: the code now fits the width available, so 8-digit codes (HOTP / 8-digit TOTP) survive the largest step.

### Other

- 7 new UI strings, added in all 11 languages.
- No new permissions, no third-party SDKs, no data collection.
