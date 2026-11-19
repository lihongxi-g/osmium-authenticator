# Osmium v2.6.5.3

**发布日期 / Release date: 2026-11-19** · versionCode 951–953（951 = armeabi-v7a、952 = arm64-v8a、953 = x86_64）

> 本版为正式版，2.6.5.2 及更早版本都会收到本次更新。
>
> This is a stable release; users on 2.6.5.2 and older will be offered it on both channels.

## 中文更新说明

### 新增：支持使用自签名证书的 WebDAV 服务器

- 局域网里的 WebDAV（rclone、NAS、反向代理）几乎都用自签名证书。之前应用只信任系统内置的证书机构，这类服务器一律握手失败，只能退回应用本身会警告的明文 `http://`。
- 现在可以「信任这一张证书」：连接时应用读取服务器出示的证书（只做握手，不发请求行、不带凭据），把它的 SHA-256 指纹显示给你；你确认后这条指纹才会保存到该服务器的设置里。
- 之后每次连接都会在写出请求（含凭据）之前比对证书，证书被换掉的话什么也拿不到。指纹格式不对一律当作「未设置」，回落到正常的系统证书校验，绝不会降级成更弱的检查。
- 只在 `https://` 地址出现该提示，页面上写明当前信任的指纹，并提供「重置信任」——服务器续期换证书后确认新指纹即可，手机里不需要安装任何 CA。

### 新增：记住备份密码

- 之前手动备份每次都要重新输入导出密码。现在备份对话框里默认勾选「记住此密码」，密码以 Android Keystore 密钥加密保存，点「立即备份」直接加密上传，不再询问。
- 新增三级页面「WebDAV 备份 → 备份密码」，可以设置、更换或清除记住的密码。清除后下次备份会重新询问；服务器上已有的备份仍用创建时的密码，不受影响。
- 恢复备份故意保持每次询问密码：旧备份可能用的是另一个密码，静默失败比多问一句更糟。

### 修复：明文警告每次都弹、备份后重复弹「设置已保存」

- 明文 `http://` 的警告改成一次性免责声明：同意后会记录，之后不再针对该地址提示（对话框里也写明了，按钮文案是「同意，不再询问」）。
- 备份自己保存表单时不再弹「服务器设置已保存」，只显示上传结果；你手动点保存仍然会确认。
- 在备份过程中同意该免责声明，会继续这次备份，而不是把你丢回表单。

### 文档与法务

- 隐私政策与用户协议同步更新（10 种语言）：说明备份密码的记住/加密存储、受信任证书指纹以明文保存但不属于机密、自签名证书的信任与重置、明文 `http://` 的后果与一次性确认、以及恢复备份始终询问密码。生效日期与「适用于 v2.6.5.3」一并更新。
- `WEBDAV-GUIDE`（中英）补上自签名证书的确认方法（附 `openssl x509 -fingerprint -sha256` 核对命令）。

## English notes

### New: WebDAV servers with a self-signed certificate

- A WebDAV server on the local network (rclone, a NAS, a reverse proxy) almost always presents a self-signed certificate. The client only trusted the platform CAs, so every such server failed the handshake and the only fallback — plain `http://` — is what the app warns against.
- You can now trust exactly one certificate per server. The app reads the certificate the server presents (handshake only: no request line, no credentials) and shows its SHA-256 fingerprint; only after you confirm is that fingerprint stored with the server settings.
- Every later connection compares the presented certificate with the pin before the request — credentials included — is written, so a substituted certificate receives nothing. A pin that is not a well-formed fingerprint counts as "no pin" and falls back to normal CA validation, never to a weaker check.
- The prompt appears for `https://` addresses only, states the fingerprint currently trusted, and offers a reset so a renewed certificate can be re-confirmed. No CA is installed on the device.

### New: the backup password can be remembered

- Manual WebDAV backups used to ask for the export password on every run. The backup dialog now offers **Remember this password** (ticked by default): it is stored encrypted with the Android Keystore key, and **Back up now** encrypts and uploads straight away.
- A new third-level page, **WebDAV backup → Backup password**, sets, changes or forgets the stored password. Forgetting it makes the next backup ask again; backups already on the server keep the password they were created with.
- Restore deliberately still asks for the password: an older backup may have been created with a different one, and a failed silent restore is worse than a prompt.

### Fixed: the plaintext warning came back every time, and a backup re-showed "settings saved"

- The plaintext `http://` warning is now a one-time disclaimer. Accepting it is recorded, so that address never prompts again (the dialog says so, and the button reads "Agree, don't ask again").
- A backup's own save no longer raises the "server settings saved" toast on top of the upload result; tapping Save yourself still confirms.
- Accepting the disclaimer during a backup continues that backup instead of dropping you back on the form.

### Docs and legal

- The Privacy Policy and the Terms of Use were updated in all 10 languages: the remembered backup password and how it is stored, the trusted certificate fingerprint being kept as plain non-secret data, how trusting and resetting a self-signed certificate works, what a plaintext `http://` address exposes and that the warning is acknowledged once, and that restoring always asks for the password. Effective date and "applies to v2.6.5.3" were bumped with it.
- `WEBDAV-GUIDE` (EN and ZH) now explains how to confirm a self-signed certificate, including the `openssl x509 -fingerprint -sha256` command.

## APK SHA-256

```
7758fa38e2d1121fa1f1073e0f405f2873de2532b6b680a052d10df432cd38e8  osmium-2.6.5.3-arm64-v8a.apk
d6b6ef8a61ae5f3090f9f64e10b42ebf6490ceba28044b0bb89a55307f344a42  osmium-2.6.5.3-armeabi-v7a.apk
84b8a115d75c8a283d2ab889463912469e6eb91b74c6e1a24cc6cef65ee57d08  osmium-2.6.5.3-x86_64.apk
```

签名证书 SHA-256 指纹（三个包相同）：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
