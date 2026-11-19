# Osmium v2.6.5.3

**2026-11-19** · versionCode 951–953(951 = armeabi-v7a、952 = arm64-v8a、953 = x86_64)· 正式版

## 更新内容

- 新增:支持使用自签名证书的 WebDAV 服务器。首次连接时显示该证书的 SHA-256 指纹,确认后仅接受此证书;证书更换后需重新确认。
- 新增:可保存备份密码。密码由 Android Keystore 密钥加密存储于本机,保存后「立即备份」不再询问;可在 WebDAV 备份 → 备份密码 中更改或清除。恢复备份仍需输入密码。
- 变更:明文 `http://` 地址的提示改为仅在首次保存时显示,确认后不再重复。
- 修复:执行备份时不再额外提示「服务器设置已保存」。
- 变更:隐私政策与用户协议同步更新(10 种语言),生效日期为 2026-11-19。

## Changes

- New: WebDAV servers using a self-signed certificate are supported. The certificate's SHA-256 fingerprint is shown on the first connection and only that certificate is accepted afterwards; re-confirmation is required once the certificate is renewed.
- New: The backup password can be saved. It is stored on the device encrypted with the Android Keystore key, and Back up now stops prompting once it is set. Change or clear it under WebDAV backup → Backup password. Restoring still requires the password.
- Changed: The plaintext `http://` notice is shown only on the first save and is not repeated after it is accepted.
- Fixed: A backup no longer shows a redundant "server settings saved" message.
- Changed: Privacy Policy and Terms of Use updated in all 10 languages; effective date 2026-11-19.

## APK SHA-256

```
7758fa38e2d1121fa1f1073e0f405f2873de2532b6b680a052d10df432cd38e8  osmium-2.6.5.3-arm64-v8a.apk
d6b6ef8a61ae5f3090f9f64e10b42ebf6490ceba28044b0bb89a55307f344a42  osmium-2.6.5.3-armeabi-v7a.apk
84b8a115d75c8a283d2ab889463912469e6eb91b74c6e1a24cc6cef65ee57d08  osmium-2.6.5.3-x86_64.apk
```

签名证书 SHA-256(三个包相同):`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
