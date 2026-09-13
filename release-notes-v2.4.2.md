# Osmium v2.4.2

**发布日期 / Release date: 2026-09-13**

## 中文更新说明

v2.4.2 新增设备 Root 检测与安全加固、开发者模式；用户协议与隐私政策改为在线获取，并修复了明文备份的导入问题。

### 新功能

- **设备 Root 检测（纯本地）**——应用启动时在设备本地检测 Root 状态：
  - 检测到 Root 后，打开时验证、禁止截屏、隐藏验证码三项安全功能**强制开启并锁定**；局域网快捷传输、WebDAV 备份、自动备份、自毁模式设置、从第三方验证器导入五项功能**停用**（点击时给出提示，可在开发者模式中解除限制）。
  - 首次检测到 Root 会显示一次说明弹窗，确认后不再出现。
  - 检测全程在本机完成：不联网、不上报；日志仅记录命中的信号名称，不记录任何密钥或文本内容。
- **开发者模式（默认隐藏）**——在「关于」页连续点击 “Osmium” 标题 7 次并通过身份验证后开启；设置中新增独立入口，每次进入都需重新验证。提供：手动 Root 检测（逐项信号与原始值报告）、KeyStore 加密状态检测、解除 Root 安全限制、导出明文密钥、强制重新加密 Keystore 数据库、隐藏指定设置项、支持 4/5/7 位验证码。危险操作需通过「身份验证 → 免责声明 → 逐字输入确认短语」三重确认；关闭开发者模式会重置危险开关。界面仅提供英文与简体中文。
- **明文备份导入修复**——开启开发者模式的明文导出后，导入页新增「明文备份（无密码导入）」选项：明文文件无需备份密码即可直接导入；文件格式严格校验，导入模式不匹配时会给出明确提示。
- **在线用户协议与隐私政策**——应用每次打开时从官网 osmium.im 获取最新版本文档（10 种语言）；获取失败时显示官网链接。文档不再内置于安装包，始终以官网当前版本为准。
- 版本号 **2.4.2（versionCode 52）**，可覆盖安装 v2.4.0 及更早版本。

### 安全说明

- Root 检测综合多类信号判断：已知 Root 管理器包名、挂载痕迹与注入库、内核特征串、/data/adb 路径等；仅解锁 Bootloader 而未 Root 的设备不会被误判。
- Root 限制用于保护设备上已有账户的安全；所有限制均可在开发者模式中解除，解除前需多重确认。
- 明文导出会使密钥以未加密形式落盘，请仅在完全理解风险时使用，并妥善保管导出文件。

## English release notes

v2.4.2 adds on-device root detection and hardening, a developer mode, online legal documents, and fixes plaintext backup import.

### New

- **Root detection (on-device, offline)** — checked locally at launch:
  - On a rooted device the security features (verification on open, screenshot blocking, hidden codes) are **forced on and locked**, and five features are **disabled**: LAN quick transfer, WebDAV backup, automatic backup, self-destruct setup and third-party import (each shows an explanation when tapped; lift the restriction in developer mode).
  - A one-time notice appears when root is first detected; it never shows again after you confirm.
  - The check is entirely local: no network, no telemetry; logs record only matched signal names, never secrets or text content.
- **Developer mode (hidden by default)** — enable by tapping the “Osmium” title on the About screen seven times and passing verification; a dedicated Settings section appears and re-authenticates on every entry. Tools: manual root check with a per-signal report, Keystore encryption status, lifting root security restrictions, plaintext export of secrets, forced re-encryption of the Keystore database, hiding selected settings entries, and 4/5/7-digit code support. Dangerous actions require three-step confirmation (identity verification → disclaimer → typed phrase); turning developer mode off resets the dangerous toggles. The interface is English / Simplified Chinese only.
- **Plaintext backup import fix** — with developer-mode plaintext export enabled, the import screen offers a “Plaintext backup (no password)” option: plaintext files import without a backup password; files are strictly validated and mismatched modes get a precise error.
- **Online Terms & Privacy** — the app fetches the latest documents from osmium.im every time it opens (10 languages); if fetching fails, a website link is shown. The documents are no longer bundled; the website version is always authoritative.
- Version **2.4.2 (versionCode 52)** — installs over v2.4.0 and earlier versions.

### Security notes

- Root detection combines several signal groups: known root-manager package names, mount/injection traces, kernel signatures, /data/adb paths and more; unlocked-bootloader devices without root are not flagged.
- The root restriction protects the accounts already on the device; every restriction can be lifted from developer mode with multiple confirmations.
- Plaintext export writes unencrypted secrets to disk — use it only with full understanding of the risk and keep the exported file safe.
