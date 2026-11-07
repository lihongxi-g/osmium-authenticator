# Osmium v2.6.2.1

**发布日期 / Release date: 待定 (TBD)**

这是一个修标签功能的小版本：去掉标签筛选行里的「未分类」，把标签配色改亮、编辑器能显示当前颜色、重名不再闪退，顺手修了几处标签相关的毛病。关于页的 X（推特）入口已移除。

## 中文更新说明

### 变更：标签筛选行去掉「未分类」

- 开启标签功能后，筛选行原来固定有一个「未分类」chip，现在没有这个入口了；没打标签的账户照常在「全部」里显示。
- 「全部」chip 仍然在，点它清空当前选择，一键回到全部账户。
- 为这个入口单独写的一整套状态（ViewModel 的第二个筛选模式、筛选函数的独立参数、界面里的一串判断分支）一并删除，行为没有残留分支。

### 变更：标签配色改亮、更容易分辨

- 调色板换成 12 个区分度高的色相，亮度提到中等：原来的 12 个颜色都在 M3 40 号色调附近，深色主题下几乎是一片近黑的圆点。
- 预设色标（红/橙/黄/绿/青/蓝/紫）的取值同步提到新色板，老数据不用迁移。
- 编辑器里改成 36dp 实心圆形色块：选中项加 2.5dp 主色描边 + 对勾（勾的颜色按色块亮度自动取黑/白），未选中加 1dp 描边，深色主题下也看得出边界。

### 修复：编辑标签时看不到当前颜色

- 新建标签的默认色存的是预设名 `blue`，而调色板里只有十六进制值，所以点编辑时 12 个色块**一个都不是选中状态**、自定义色输入框也是空的——看起来像这条标签没颜色。
- 现在编辑器把存着的颜色统一解析成十六进制再比对色板，`blue` 这类老值也会正确点亮对应色块；输入框直接显示当前颜色值。

### 修复：重名标签会让应用闪退

- 标签仓储层用 `require()` 查重，抛出的是未捕获异常；界面这边只校验了非空和 20 字上限，没有查重。输入一个已存在的名字（大小写不同也算）点保存 → 进程被带走。
- 现在编辑器在保存前查重并就地提示「已存在同名标签。」，仓储层的异常也在 ViewModel 里兜住，双保险。

### 修复：中文标签排序是乱的

- 列表原来用 `ORDER BY name COLLATE NOCASE` 排序，而 NOCASE 只对 ASCII 生效，中文按码点排，对读的人是随机顺序。
- 改为在仓储层用 `java.text.Collator`（跟随系统语言）排序，中文标签按拼音顺序排列，英文/其它语言同样走本地化排序。

### 修复：几处小毛病

- 删除确认框现在写明「只会解除“标签名”与 N 个账户的关联」，删除按钮用错误色；之前完全不提影响多少个账户。
- 名称和颜色的错误提示分开显示（各自挂在对应输入框下），不再共用一个位置、一次只显示一条。
- 自定义颜色输入框只接受数字和 A-F、自动补 `#`、最长 7 位，不会在打字中途留下非法值。
- 编辑器顶部加了实时预览：色点 + 名称 + 十六进制值，改色改名的效果立刻可见。

### 移除：关于页的 X（推特）入口

- 关于页「关注」一栏去掉了 X 链接，保留 Telegram、GitHub、邮件、官网四项。

## English release notes

A small release that reworks the tags feature: the "Uncategorized" filter chip is gone, the palette is brighter, the editor shows the color a tag already has, and a duplicate name no longer takes the process down. The About page no longer links to X.

### Changed: no more "Uncategorized" chip

- With tags enabled, the filter row used to carry a permanent "Uncategorized" chip. It is gone; accounts without a tag simply appear under "All".
- The "All" chip stays: it clears the current selection in one tap.
- The state that existed only for that chip (a second filter mode in the ViewModel, an extra parameter on the filter function, the branching in the UI) was removed with it.

### Changed: brighter, easier to tell apart tag colors

- The palette is now twelve distinct hues at a medium brightness. The previous twelve sat around M3 tone 40, which on the dark theme rendered as a row of near-black dots.
- The named presets (red/orange/yellow/green/cyan/blue/purple) resolve to the same new colors, so nothing needs migrating.
- Swatches are 36dp filled circles: the selected one gets a 2.5dp primary ring plus a check mark (black or white depending on the swatch's luminance), the rest a 1dp outline, so the grid stays legible on the dark theme.

### Fixed: the editor hid the tag's current color

- A new tag defaults to the preset name `blue`, while the palette only listed hex values — so opening the editor selected **none** of the twelve swatches and left the custom-color field empty. It looked like the tag had no color at all.
- The editor now resolves the stored value to hex before matching the grid, so legacy values such as `blue` light up the right swatch, and the field shows the current value.

### Fixed: a duplicate tag name crashed the app

- The repository rejects duplicates with `require()`, which throws; the editor only validated emptiness and the 20-character cap. Saving an existing name (differing case included) killed the process.
- The editor now checks for duplicates first and reports "A tag with this name already exists." inline, and the repository's throw is caught in the ViewModel as a second line of defence.

### Fixed: Chinese tag names were sorted randomly

- The list was ordered with `ORDER BY name COLLATE NOCASE`, which is ASCII-only; CJK names came back in code-point order, i.e. random to a reader.
- Ordering now happens in the repository through `java.text.Collator` (following the system locale), so Chinese names sort in pinyin order and other languages sort properly too.

### Fixed: smaller things

- The delete prompt now says how many accounts are affected ("only unlinks … from N accounts") and the destructive button uses the error color.
- The name and color errors are reported separately, each under its own field, instead of sharing one slot.
- The custom-color field accepts hex digits only, prefixes `#` automatically and caps at seven characters, so it cannot be left in an invalid state mid-typing.
- The editor has a live preview at the top: color dot, name and hex value update as you edit.

### Removed: the X link on the About page

- The "Follow" row no longer links to X; Telegram, GitHub, email and the website remain.

### APK checksums (SHA-256)

```
167ad203801ac902da132ce981a6fe1586fd56bd4d5509af6f562577080883d6  osmium-2.6.2.1-arm64-v8a.apk
325d7a3595c78f0f80c1355c09560f6f0c1fbb1bcfb3b6fee2974e97f0e0b047  osmium-2.6.2.1-armeabi-v7a.apk
505eb7a743c5228e2a49df09dd9d0e0f5e3ce2e4cf2cb9881b6f57c9477b61eb  osmium-2.6.2.1-x86_64.apk
```

Built by GitHub Actions run 42324942136 (472 app + 146 keyattestation unit tests, 0 failed). All three APKs are signed with the certificate `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`, the same key as v2.6.2, so they upgrade in place.
