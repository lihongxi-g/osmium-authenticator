# Osmium v2.6.2

**发布日期 / Release date: 2026-11-XX**

本次更新新增「字体大小」设置（80%–200% 自由调节），并修掉多处在长译文 + 大字号下文字被裁掉、又无法滑动查看的问题。

## 中文更新说明

### 新增：字体大小，可自由调节

- 设置 → 外观 → **字体大小**：滑块 80%–200%，每 5% 一档，松手即生效，不需要重启。旁边保留 小 / 标准 / 大 / 特大 四个快捷档。
- 倍率是**乘在系统字号之上**的：系统设置里已经把字调大的用户还能再放大；100% 等于完全跟随系统。
- 页面顶部有实时预览：拖动滑块时，验证码样例和账号名立刻跟着变，松手才保存。
- 验证码和界面文字一起变化，图标、间距、卡片宽度不变。
- 合计放大封顶 2.0 倍（应用 × 系统），保证 PIN 键盘（固定 72dp 圆键）和账号卡片不会被撑破。

### 修复：长译文不再被裁掉、无法查看

- **锁屏、PIN 输入页、扫码页的权限说明**：内容超出屏幕时整页可以滑动。之前是居中固定布局，长英文/德文加上大字号会把按钮顶出屏幕，且没有滚动。
- **系统对话框**（防篡改提示、完整性报告、WebDAV HTTP 警告、备份密码、自毁模式说明、导出告别页等）：正文过长时现在可以在对话框内滚动查看。
- **账号卡片**：固定 96dp 高改为最小高度 96dp，标题 + 副标题 + 标签行不再被裁；默认字号下外观与之前完全一致。
- **详情页验证码**：按可用宽度自适应，8 位码（HOTP / 8 位 TOTP）在最大倍率下也不会被切掉。
- 英文、德文、俄文等长译文在上述页面均已覆盖。

### 其他

- 新增 7 条界面文案，11 种语言同步。
- 无新增权限、无第三方 SDK、无数据收集。

## English release notes

This release adds a text-size setting with a free 80%–200% slider, and fixes several layouts where long translations were cut off with no way to scroll to the rest.

### New: text size you can set yourself

- Settings → Appearance → **Text size**: an 80%–200% slider in 5% steps, applied when you let go, no restart. Small / Default / Large / Largest remain as quick presets.
- The multiplier sits **on top of** the system font scale: users who already enlarged text system-wide can go further, and 100% follows the system exactly.
- A live preview at the top of the page: dragging the slider re-renders the sample code and account name immediately; releasing saves.
- Codes and interface text grow together; icons, spacing and card width stay put.
- The combined scale is capped at 2.0× (app × system) so the PIN keypad (fixed 72dp keys) and the account cards cannot be pushed past what they can hold.

### Fixes: long translations are no longer cut off

- **Lock screen, PIN pad and the camera-permission page**: the whole page now scrolls when the content is taller than the screen. These were centred fixed layouts, so a long English or German string at a large text size pushed the buttons off-screen with no way to reach them.
- **System dialogs** (tamper warning, integrity report, WebDAV HTTP warning, backup password, self-destruct explanation, export farewell): the body now scrolls inside the dialog when it is too long.
- **Account cards**: fixed 96dp height became a 96dp minimum, so title + subtitle + tag row are no longer clipped; at the default size the card looks exactly as before.
- **Detail-page code**: fits the width available, so 8-digit codes (HOTP / 8-digit TOTP) survive the largest multiplier.
- Covered for English, German, Russian and the other long-string locales.

### Other

- 7 new UI strings, added in all 11 languages.
- No new permissions, no third-party SDKs, no data collection.
