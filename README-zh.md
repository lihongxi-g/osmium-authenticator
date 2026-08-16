# Osmium

Osmium 是一款隐私优先的 Android TOTP 验证器。所有密钥在落盘前均使用硬件级 Android Keystore 密钥加密。应用无需注册账号、不收集任何遥测——验证码完全在本机计算。INTERNET 权限仅用于可选的 WebDAV 备份功能：应用只连接您自行配置的服务器地址（通常是局域网内的 NAS），不会连接其他任何地址。

## 功能

- **TOTP 与 HOTP**——支持 SHA-1 / SHA-256 / SHA-512，6 位或 8 位，周期 1–600 秒
- **Google 验证器迁移**——扫描 Google Authenticator 的「转移账户」二维码，一步导入全部账户
- **加密备份**——导出为密码保护、绑定应用 PIN 的加密文件，可在任意设备恢复
- **WebDAV 备份**——将同一份加密导出文件上传到局域网内的 WebDAV 服务器（NAS、电脑、另一部手机）并从中恢复；服务器只存放密文
- **Steam Guard**——手动添加，使用 26 字符 Steam 字母表（见下方警告）
- **隐藏验证码模式**——验证码以圆点显示，复制不受影响；编辑与分享在关闭该模式前被锁定
- **排序方式**——随机、字母、添加时间、复制次数
- **时钟校准**——设备时间漂移时手动补偿
- **安全门禁**——可选的打开时验证（指纹 / 系统密码 / 应用 PIN）、自毁 PIN、默认禁止截屏
- **九种语言**——简体中文、English、Español、日本語、한국어、Deutsch、Русский、Français、हिन्दी
- **内置使用手册**——功能说明与注意事项
- **内置协议与隐私政策**——设置 → 关于 页内可查看用户协议与隐私政策
- **账户名与服务商可留空**——账户名留空时按添加日期与位次自动命名（如 `20260801`）；服务商留空时显示 `Unknown`

## ⚠ Steam Guard 使用前必读

**手动添加 Steam 账户时，必须在服务商（issuer）一栏填写 `Steam`，否则该账户会被当作普通 TOTP 处理，生成的验证码是错误的。** Steam Guard 验证码是 5 位字母数字组合，不是 6 位纯数字。

## 验证你的验证器

在线测试站 **https://otp.osmium.im** ——发放测试密钥，添加进任意验证器后回填验证码即可校验。支持 TOTP、HOTP 与 Steam Guard，全部在浏览器本地完成，不上传任何数据。

## 下载

请选择与设备匹配的架构安装包：

| 文件 | 架构 | 适用设备 |
|---|---|---|
| `app-arm64-v8a-release.apk` | arm64-v8a | 几乎所有现代手机（推荐） |
| `app-armeabi-v7a-release.apk` | armeabi-v7a | 老款 32 位手机 |
| `app-x86_64-release.apk` | x86_64 | 模拟器 |

安装与设备架构不符的包会导致启动闪退。签名证书 SHA-256 指纹：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

## 截图

> 图片经自建图床直链加载（GitHub 图片代理在大陆网络下可能无法显示）。

| WebDAV 备份配置 | 连接测试 |
|---|---|
| ![](https://159310.xyz/img/osmium-webdav-config-6hvyu.jpg) | ![](https://159310.xyz/img/osmium-webdav-toast-fnap6.jpg) |

| 备份上传完成 | 选择备份 |
|---|---|
| ![](https://159310.xyz/img/osmium-webdav-upload-p636y.jpg) | ![](https://159310.xyz/img/osmium-webdav-picker-r260d.jpg) |

| 导入预览 |
|---|
| ![](https://159310.xyz/img/osmium-webdav-import-wpm28.jpg) |

## 隐私与条款

- [隐私政策](PRIVACY.md)（[中文版](PRIVACY-zh.md)）
- [用户协议](TERMS.md)（[中文版](TERMS-zh.md)）

应用内 设置 → 关于 亦可查看。

## WebDAV 备份教程

各平台（NAS、Linux、Android、macOS、Windows、iOS、鸿蒙）搭建 WebDAV 服务器的分步教程：[WEBDAV-GUIDE-zh.md](WEBDAV-GUIDE-zh.md)（[English](WEBDAV-GUIDE.md)）

## 安全说明

- 字段级 AES-256-GCM 加密，密钥不可导出，存于 Android Keystore
- INTERNET 权限仅用于用户自行配置的 WebDAV 备份服务器；应用不会连接其他任何地址——无统计、无崩溃上报、无云
- 备份在离开设备前采用 PBKDF2 + AES-256-GCM 加密，并与应用 PIN 绑定
- HTTPS 使用标准证书校验——自签证书按设计直接拒绝（密码类应用绝不内置 TrustAllManager）
- 自毁 PIN 可在任意 PIN 输入处触发不可逆的数据销毁

## 构建

```bash
./gradlew assembleRelease
```

Release 构建启用 R8 混淆并按 ABI 分包。单元测试：`./gradlew testDebugUnitTest`。

## 许可证

MIT
