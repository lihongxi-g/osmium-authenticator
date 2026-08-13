# SafeKey

离线优先、隐私优先的 Android TOTP 身份验证器（Material 3 + Jetpack Compose）。

## 特性

- **完全离线**：无网络权限，不依赖任何服务器，不注册账号
- **端到端加密**：issuer / 账户名 / 密钥全部使用 Android Keystore AES-256-GCM 字段级加密落盘
- **生物识别锁**：指纹 / 人脸 / 锁屏密码保护整个 App，防截图（FLAG_SECURE）
- **TOTP 完整支持**：SHA1 / SHA256 / SHA512，6 / 8 位，自定义 period（1–600s）
- **扫码添加**：CameraX + ML Kit 离线识别 otpauth:// 二维码，支持手动输入与链接粘贴
- **加密导入 / 导出**：PBKDF2-HMAC-SHA256 + AES-256-GCM，导入前预览确认
- **剪贴板自动清理**：复制验证码后按设定时间自动清空
- **拖拽排序、搜索、深色模式、Dynamic Color**

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM + Repository · Room · DataStore ·
Android Keystore · CameraX · ML Kit Barcode Scanning · kotlinx-serialization

minSdk 26 (Android 8.0) / targetSdk 34

## 构建

GitHub Actions（push 到 main 自动触发），或本地：

```bash
./gradlew assembleDebug
```

## 测试

纯 JVM 单元测试（RFC 6238 官方向量、Base32、URI 解析、导入导出加密）随 CI 运行：

```bash
./gradlew testDebugUnitTest
```

Keystore 加解密与生物识别需真机验证：

```bash
./gradlew connectedDebugAndroidTest
```
