# Osmium v2.4.0

**发布日期 / Release date: 2026-09-06**

## 中文更新说明

v2.4.0 为 Osmium 带来「多验证器迁移导入」：除已有的 Google Authenticator
二维码迁移外，现在可以直接读取主流验证器导出的文件并一键迁入。

### 新功能

- **从其他验证器导入（文件）**——设置 → 数据 → 「从其他验证器导入」。
  支持来源与导出方式：
  - **Aegis**：Settings → Import & Export → 导出备份时**不设置密码**（明文导出）
  - **andOTP**：明文备份（JSON，无需密码）
  - **2FAS**：2FAS 备份导出时**取消勾选密码**（.2fas 明文）
  - **Raivo OTP**：旧版明文 JSON 导出（2023 年收购前的备份格式）
  - **LastPass Authenticator**：设置 → 转移账户 → 导出到文件（accounts.json）
- 自动识别文件格式，无需手动选择来源应用；解析后在应用内**逐条预览勾选**，
  与已有账户按「服务商 + 账户名」匹配去重（同名账户导入后更新，不重复添加）。
- 不支持的条目（非 TOTP/HOTP 类型、非 SHA1/SHA256/SHA512 算法、非 6/8 位
  验证码、无效密钥等）会**列出原因并置灰**，不会静默丢弃，也不会以错误参数导入。
- Steam 条目按 Osmium 规则转为「issuer = Steam」的账户，自动生成 5 位 Steam 码。
- HOTP 账户导入保留原计数器；重复导入同一文件只会提示更新，不会产生重复账户。
- 带密码加密的导出文件（Aegis/2FAS 等）暂不支持，会给出明确的重新导出提示。

### 其他

- 应用内「用户协议」与「隐私政策」已按 v2.4.0 功能重写（关于页面可离线查看；
  仓库内 TERMS.md / PRIVACY.md 及中文版同步更新），明确说明文件导入全程在本机完成。
- 新文件导入不新增任何权限；文件选择走系统 SAF，读取与解析均在本机进行。

## English release notes

Osmium v2.4.0 adds file-based migration from other authenticator apps, next to
the existing Google Authenticator QR migration.

### New

- **Import from another authenticator (file)** — Settings → Data.
  Supported sources:
  - **Aegis**: Settings → Import & Export, export *without* a password (plaintext)
  - **andOTP**: plaintext JSON backup (no password)
  - **2FAS**: 2FAS backup exported with the password option *unchecked* (.2fas)
  - **Raivo OTP**: legacy plain-JSON export (pre-2023 format)
  - **LastPass Authenticator**: Settings → Transfer accounts → Export accounts to file
- The format is auto-detected from the file — no need to pick the source app.
  Every entry is shown in a preview with checkboxes; existing accounts with the
  same issuer and name are updated instead of duplicated.
- Entries Osmium cannot reproduce (non-TOTP/HOTP types, algorithms other than
  SHA1/SHA256/SHA512, code lengths other than 6/8, invalid secrets) are listed
  with a reason and disabled — never silently dropped, never imported with
  mangled parameters.
- Steam entries map to issuer = Steam accounts with automatic 5-character
  Steam Guard codes; HOTP counters are preserved on import.
- Password-encrypted exports are not supported yet and show a clear hint to
  re-export without a password.

### Other

- The in-app Terms of Use and Privacy Policy were rewritten for the v2.4.0
  feature set (also mirrored in TERMS.md / PRIVACY.md and the Chinese docs),
  making explicit that file imports are processed entirely on-device.
- No new permissions: the import screen uses the system file picker (SAF) and
  reads the file locally.
