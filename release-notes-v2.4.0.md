# Osmium v2.4.0

**发布日期 / Release date: 2026-09-06**

## 中文更新说明

v2.4.0 将迁移导入统一为「第三方验证器导入」：设置 → 数据 →
「从其他验证器导入」，先选择来源应用，再按对应方式完成迁移。

### 新功能

- **统一的第三方验证器导入入口**——设置 → 数据 → 从其他验证器导入。
  选择来源应用后进入对应流程，底部注明本功能为新功能、并提示导入失败时
  如何附日志反馈。
- **Google Authenticator**：扫描「转移账户」二维码批量导入（每批约 10 个）。
- **Aegis**：导入 Aegis 的明文导出文件（导出时**不设置密码**，.json）。
- **2FAS**：导入无密码导出的备份（.2fas，导出时取消勾选密码）。
- **Raivo OTP**：导入旧版明文 JSON 导出（2023 年被收购前的备份格式）。
- 文件格式自动识别，无需手动选择；解析后**逐条预览勾选**，与已有账户按
  「服务商 + 账户名」匹配去重（同名账户导入后更新，不重复添加）。
- 不支持的条目（非 TOTP/HOTP 类型、非 SHA1/SHA256/SHA512 算法、非 6/8 位
  验证码、无效密钥等）会**列出原因并置灰**，不会静默丢弃，也不会以错误参数导入。
- Steam 条目按 Osmium 规则转为「issuer = Steam」账户，自动生成 5 位 Steam 码；
  HOTP 导入保留原计数器。
- 带密码加密的导出暂不支持，会给出明确的重新导出提示。
- **关于 → 来源说明**：新增二级页面，逐条说明导入功能所参考的开源项目与
  格式文档（含许可证），点击即可在浏览器中打开对应项目页面。
- **全语言同步**：新增文案与历史遗漏的字符串已补齐全部界面语言。

### 其他

- 应用内「用户协议」与「隐私政策」已同步更新（文件导入全程在本机完成，
  不复制不上传）；仓库 TERMS.md / PRIVACY.md 及中文版保持一致。
- 新文件导入不新增任何权限；文件选择走系统 SAF，读取与解析均在本机进行。

## English release notes

v2.4.0 unifies migration under one entry: Settings → Data →
"Import from another authenticator". Pick the source app first, then follow
its import flow.

### New

- **Unified third-party import** — Settings → Data → Import from another
  authenticator. The page lists every supported source; a note at the bottom
  marks the feature as new and explains how to report import failures with
  the log attached.
- **Google Authenticator**: scan the "Transfer accounts" QR codes (~10 per
  batch).
- **Aegis**: import its plaintext export file (.json, exported *without* a
  password).
- **2FAS**: import a backup exported without a password (.2fas).
- **Raivo OTP**: import the legacy plain-JSON export (pre-2023 format).
- File formats are auto-detected — no need to pick a source. Every entry is
  shown in a preview with checkboxes; existing accounts with the same issuer
  and name are updated instead of duplicated.
- Entries Osmium cannot reproduce (non-TOTP/HOTP types, algorithms other than
  SHA1/SHA256/SHA512, code lengths other than 6/8, invalid secrets) are listed
  with a reason and disabled — never silently dropped, never imported with
  mangled parameters.
- Steam entries map to issuer = Steam accounts with automatic 5-character
  Steam Guard codes; HOTP counters are preserved on import.
- Password-encrypted exports are not supported yet and show a clear hint to
  re-export without a password.
- **About → Attributions & sources**: new page documenting the open-source
  projects and format references behind the import feature (with licenses);
  tapping a card opens the project page in the browser.
- **Full localization sync**: new strings and previously missing translations
  are now complete in every supported language.

### Other

- The in-app Terms of Use and Privacy Policy were updated accordingly
  (file imports are processed entirely on-device; nothing is copied or
  uploaded); TERMS.md / PRIVACY.md and the Chinese docs mirror the in-app text.
- No new permissions: the import flow uses the system file picker (SAF) and
  reads files locally.
