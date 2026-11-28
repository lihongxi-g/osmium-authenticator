# Osmium v2.6.6.3

versionCode 963 · 通用 APK / Universal APK (arm64-v8a, armeabi-v7a, x86, x86_64)

## 更新内容

- 备份加密新增可选的 Argon2id 密钥派生（RFC 9106 第二推荐参数：m = 64 MiB 或 128 MiB，t = 3，p = 4），可按文件导出、WebDAV 备份、自动备份分别设置；原有 PBKDF2-HMAC-SHA256（12 万次）继续可用。
- 密钥派生参数写入备份文件本身，恢复时以文件中的参数为准；升级前导出的备份照常读取；AES-256-GCM 与 salt、IV 结构未变。

## Changes

- Backups can now use Argon2id key derivation (RFC 9106 second recommendation: m = 64 MiB or 128 MiB, t = 3, p = 4) in place of PBKDF2-HMAC-SHA256 (120,000 iterations), chosen separately for file export, WebDAV and automatic backup.
- The KDF parameters are stored inside the backup file and used when restoring; backups exported before this release still open; AES-256-GCM with its salt and IV layout is unchanged.

APK: `osmium-2.6.6.3.apk` · SHA-256: see the attached `SHA256SUMS.txt`.
