# Osmium v2.4.0

**发布日期 / Release date: 2026-09-06**

## 中文更新说明

v2.4.0 将迁移导入统一为「第三方验证器导入」：设置 → 数据 →
「从其他验证器导入」，先选择来源应用，再按对应方式完成迁移。

### 新功能

- **统一的第三方验证器导入入口**——设置 → 数据 → 从其他验证器导入。
  支持来源：**Google Authenticator**（扫描「转移账户」二维码，每批约 10 个）、
  **Aegis**（明文导出 .json）、**2FAS**（无密码导出 .2fas）、**Raivo OTP**
  （旧版明文 JSON）。
- **文件格式自动识别**：导入页按文件**内容**自动判断格式——来源列表只是
  引导入口，从任一来源进入都可以导入其它来源的明文导出文件，无需手动选择
  格式；无法识别的文件会列出支持范围并提示。
- 解析后**逐条预览勾选**，与已有账户按「服务商 + 账户名」匹配去重（同名
  账户导入后更新，不重复添加）；HOTP 保留原计数器；Steam 条目自动转为
  issuer=Steam 账户。
- 不支持的条目（非 TOTP/HOTP、非 SHA1/SHA256/SHA512、非 6/8 位验证码、
  无效密钥）会**列出原因并置灰**，不会静默丢弃或以错误参数导入。
- 带密码加密的导出暂不支持，会给出明确的重新导出提示。
- **关于 → 来源说明**：新增页面逐条列出本功能参考的开源项目与格式文档，
  点击可跳转项目页面。
- **全语言同步**：11 种界面语言补齐了导入相关文案、历史遗漏字符串，
  并同步更新了各语言的用户协议与隐私政策译文。
- 版本号 **2.4.0（versionCode 47）**，可覆盖安装 v2.3.x。

### 借鉴来源（Attributions）

Osmium 的导入功能仅参考各验证器**公开的导出文件格式**编写，未复制任何
第三方代码；测试使用的真实导出样本取自 Aegis 测试套件。应用内
「关于 → 来源说明」页列出了完整来源并可点击跳转：

- **Aegis Authenticator**（GPL-3.0）— 导出格式参考与测试样本来源
  https://github.com/beemdevelopment/Aegis
- **2FAS (twofas/2fas-android)** — .2fas 备份格式参考
  https://github.com/twofas/2fas-android
- **OtpTranslate (tygertec.com)** — Raivo OTP 旧版导出格式说明
  https://tygertec.com/aegis-raivo-otp-translator

Osmium 本身基于 GNU GPL v3 或更高版本发布。

---

## English release notes

v2.4.0 unifies migration under one entry: Settings → Data →
"Import from another authenticator". Pick a source app, then follow its flow.

### New

- **Unified third-party import** — Google Authenticator (transfer QR codes,
  ~10 per batch), Aegis (plaintext .json), 2FAS (password-free .2fas) and
  Raivo OTP (legacy plain JSON).
- **Automatic format detection**: the import page identifies the format from
  the file CONTENT — the source list is only a launcher. You can import a
  plaintext export of any supported app from any entry; unrecognized files
  get a clear hint listing the supported formats.
- Every entry is previewed with checkboxes before merging; accounts with the
  same issuer and name are updated instead of duplicated. HOTP counters are
  preserved; Steam entries map to issuer = Steam accounts.
- Entries Osmium cannot reproduce (non-TOTP/HOTP types, algorithms other than
  SHA1/SHA256/SHA512, code lengths other than 6/8, invalid secrets) are listed
  with a reason and disabled — never silently dropped.
- Password-encrypted exports are not supported yet and explain how to
  re-export without a password.
- **About → Attributions & sources**: new page listing the open-source
  projects and format references behind the import feature, with tappable
  links.
- **Full localization sync**: all 11 UI languages received the import
  strings, previously missing translations, and updated Terms/Privacy
  translations.
- Version **2.4.0 (versionCode 47)** — installs over v2.3.x.

### Attributions

The import feature was written from the publicly documented export formats of
each app; no third-party code is reused. Real export samples used in Osmium's
tests come from the Aegis test suite. The in-app page About → Attributions &
sources lists everything with links:

- **Aegis Authenticator** (GPL-3.0) — format reference and test samples
  https://github.com/beemdevelopment/Aegis
- **2FAS (twofas/2fas-android)** — .2fas format reference
  https://github.com/twofas/2fas-android
- **OtpTranslate (tygertec.com)** — legacy Raivo export format
  https://tygertec.com/aegis-raivo-otp-translator

Osmium itself is released under the GNU GPL v3 or later.
