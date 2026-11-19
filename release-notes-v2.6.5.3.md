# Osmium v2.6.5.3

**2026-11-19** · versionCode 951–953(951 = armeabi-v7a、952 = arm64-v8a、953 = x86_64) · 正式版,2.6.5.2 及更早的用户两个通道都会收到

## 中文

- **自签名证书的 WebDAV 服务器能连了。** 连接时应用显示证书的 SHA-256 指纹,你确认后只认这一张,手机里不用装 CA。证书续期后要重新确认。
- **备份密码可以记住。** 对话框里默认勾选,密码存在本机、由 Keystore 加密,之后点「立即备份」不再问。换密码或清除:WebDAV 备份 → 备份密码。恢复仍然问,因为旧备份可能用的是别的密码。
- **明文 `http://` 的警告只问一次。** 之前每次备份都弹,因为备份会顺手保存表单,而警告挂在保存上。
- **备份不再多弹一句「服务器设置已保存」**,只显示上传结果。自己点保存还是会有提示。
- **隐私政策和用户协议同步更新**(10 种语言),生效日期改为 2026-11-19。

## English

- **WebDAV servers with a self-signed certificate work now.** The app shows the certificate's SHA-256 fingerprint; once you confirm it, only that certificate is accepted for that address, and no CA needs to be installed. Re-confirm after the certificate is renewed.
- **The backup password can be remembered.** Ticked by default, stored on the device encrypted with the Keystore key, so "Back up now" stops asking. Change or forget it under WebDAV backup → Backup password. Restore still asks — an older file may use a different password.
- **The plaintext `http://` warning is asked once.** It used to come back on every backup, because a backup saves the form and the warning hung off that save.
- **A backup no longer adds a redundant "server settings saved" toast** on top of the upload result. Tapping Save yourself still confirms.
- **Privacy policy and terms updated** in all 10 languages; effective date 2026-11-19.

## APK SHA-256

```
7758fa38e2d1121fa1f1073e0f405f2873de2532b6b680a052d10df432cd38e8  osmium-2.6.5.3-arm64-v8a.apk
d6b6ef8a61ae5f3090f9f64e10b42ebf6490ceba28044b0bb89a55307f344a42  osmium-2.6.5.3-armeabi-v7a.apk
84b8a115d75c8a283d2ab889463912469e6eb91b74c6e1a24cc6cef65ee57d08  osmium-2.6.5.3-x86_64.apk
```

签名证书 SHA-256(三个包相同):`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
