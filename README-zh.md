# Osmium

Osmium 是一款隐私优先的 Android TOTP/HOTP 验证器。本项目基于 GNU GPL v3 或更高版本发布。验证码在设备本地计算；账户数据使用 Android Keystore 中的不可导出密钥加密后才落盘。应用无需注册账号、不收集遥测。

应用的网络功能是可选的：用户配置的 WebDAV 备份、GitHub 更新检查，以及用户主动发起的两台设备局域网快捷传输。点击 GitHub、验证实验室等外部链接时，流量由系统浏览器或其他应用处理。

## 功能

- **TOTP 与 HOTP**——支持 SHA-1 / SHA-256 / SHA-512，6 位或 8 位，周期 1–600 秒
- **Google 验证器迁移**——扫描 Google Authenticator 的「转移账户」二维码，批量导入受支持的账户
- **从其他验证器导入**——通过「设置 → 数据」从 Aegis、2FAS、andOTP、Raivo OTP、LastPass Authenticator 的明文导出文件恢复账户；自动识别格式，逐条预览勾选后再合并导入；不支持的条目会列出原因，不会被静默丢弃
- **加密备份**——导出为密码保护的加密文件，可在安装兼容版本 Osmium 的设备上恢复
- **WebDAV 备份**——将加密备份上传到用户自行配置的 WebDAV 服务器（NAS、电脑或另一部手机）并从中恢复；服务器只接收密文
- **局域网快捷传输**——两台设备连接同一 Wi‑Fi 后，通过 6 位配对码端到端加密传输账户，不经过云端；接收前可预览并选择账户
- **自动备份**——按计划备份到 WebDAV 或手机的 下载/Osmium 目录；可设置间隔、时间和保留数量
- **更新检查**——可选查询 GitHub Releases API；只读取公开版本信息，不发送账户或设备数据，也不会自动下载或安装
- **Steam Guard**——手动添加 Steam 账户，生成 5 位字母数字验证码
- **隐藏验证码模式**——验证码以圆点显示，复制不受影响；编辑与分享在关闭该模式前被锁定
- **排序与搜索**——支持随机、字母、添加时间、复制次数排序，并可按账户名或服务商搜索
- **可选标签**——在 设置 → 外观 → 标签 中开启标签功能；可创建多标签、筛选账户，并使用调色盘或自定义 `#RRGGBB` 颜色。没有创建标签时，主页不会显示 `Uncategorized`
- **安全门禁**——可选的打开时验证（指纹 / 系统凭据 / 应用 PIN）、自毁 PIN、默认禁止截屏
- **时钟校准**——设备时间漂移时手动补偿 TOTP 时钟
- **11 种语言**——English、简体中文、繁體中文、Español、日本語、한국어、Deutsch、Русский、Français、हिन्दी
- **内置使用手册、用户协议与隐私政策**——无需联网即可查看

## ⚠ Steam Guard 使用前必读

手动添加 Steam 账户时，服务商（issuer）一栏必须填写 `Steam`，否则账户会被当作普通 TOTP 处理，验证码会错误。Steam Guard 验证码是 5 位字母数字组合，不是 6 位纯数字。

## 验证你的验证器

在线测试站 **https://otp.osmium.im** 支持 TOTP、HOTP 与 Steam Guard。测试过程在浏览器本地完成，不上传数据。

## 下载

最新正式版：**Osmium v2.3.9**（versionCode 46）。请选择与设备匹配的架构：

| 文件 | 架构 | 适用设备 | 下载 |
|---|---|---|---|
| `osmium-2.3.9-arm64-v8a.apk` | arm64-v8a | 几乎所有现代手机（推荐） | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.3.9/osmium-2.3.9-arm64-v8a.apk) |
| `osmium-2.3.9-armeabi-v7a.apk` | armeabi-v7a | 老款 32 位手机 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.3.9/osmium-2.3.9-armeabi-v7a.apk) |
| `osmium-2.3.9-x86_64.apk` | x86_64 | Android 模拟器 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.3.9/osmium-2.3.9-x86_64.apk) |

安装与设备架构不符的包可能无法启动。三个架构使用同一签名证书，SHA-256 指纹为：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

APK 的 SHA-256 校验和见 [v2.3.9 发布说明](release-notes-v2.3.9.md) 及 GitHub Release 页面。

## 隐私与条款

- [隐私政策](PRIVACY-zh.md)（[English](PRIVACY.md)）
- [用户协议](TERMS-zh.md)（[English](TERMS.md)）

应用内 设置 → 关于 也可查看。局域网传输和 WebDAV 备份均由用户主动操作触发；局域网传输使用配对码派生的端到端加密，不经过 Osmium 服务器。

## WebDAV 备份教程

各平台搭建 WebDAV 服务器的分步教程：[WEBDAV-GUIDE-zh.md](WEBDAV-GUIDE-zh.md)（[English](WEBDAV-GUIDE.md)）。WebDAV 与局域网快捷传输是两个独立功能：前者面向备份服务器，后者面向同一局域网内的另一台设备。

## 电池与后台行为

自动备份通过 Android 系统调度器执行——应用仅在备份时短暂唤醒，不驻留常驻后台服务。部分机型的省电策略可能推迟或拦截定时备份，请按 README 和应用内自动备份页面提示，为 Osmium 开启自启动及允许后台运行。

## 构建

```bash
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

Release 构建启用 R8 并按 ABI 分包。构建环境：Kotlin 1.9、Jetpack Compose BOM 2024.09.03、`compileSdk 35`、`minSdk 26`、`targetSdk 34`。GitHub Actions 会运行单元测试并构建 Release APK；正式发布资产固定命名为 `osmium-版本号-架构.apk`。

## 许可证

GPL-3.0-or-later，详见 [LICENSE](LICENSE) 或 [COPYING](COPYING)。
