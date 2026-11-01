# Osmium

Osmium 是一款隐私优先的 Android TOTP/HOTP 验证器。本项目基于 GNU GPL v3 或更高版本发布。验证码在设备本地计算；账户数据使用 Android Keystore 中的不可导出密钥加密后才落盘。应用无需注册账号、不收集遥测。

应用在你打开「设置 → 关于 → 用户协议 / 隐私政策」时才从官网 osmium.im 获取最新版本（不发送任何用户数据）；除此之外，网络功能均为可选：用户配置的 WebDAV 备份、GitHub 更新检查（正式版与预览版各一个开关，默认均关闭），以及用户主动发起的两台设备局域网快捷传输。点击 GitHub、验证实验室等外部链接时，流量由系统浏览器或其他应用处理。

## 功能

- **TOTP 与 HOTP**——支持 SHA-1 / SHA-256 / SHA-512，6 位或 8 位，周期 1–600 秒
- **Google 验证器迁移**——扫描 Google Authenticator 的「转移账户」二维码，批量导入受支持的账户
- **从其他验证器导入**——通过「设置 → 数据」迁移：Google Authenticator 转移二维码，或 Aegis、2FAS、Raivo OTP、andOTP、FreeOTP/FreeOTP+、Stratum/Authenticator Pro、Proton Authenticator、Bitwarden（JSON 或 CSV）的明文导出文件，也支持任意 otpauth 链接清单（Ente Auth、Google Auth URI 导出）与带 otpauth 链接的 CSV（KeePassXC、1Password、Dashlane）；自动识别格式，逐条预览勾选后再合并导入；不支持的条目会列出原因，不会被静默丢弃
- **导出到其它验证器**——迁移出口，两种形式：**直接以二维码形式导出到 Google Authenticator**（`otpauth-migration`，每张码 10 个账户，超出可分页，格式无法承载的条目会提前列出），或导出为文件——otpauth 链接清单（.txt）、Aegis 明文保险库（.json）、KeePassXC CSV（.csv）。文件本身即明文，页面会明确提示，且必须先通过身份验证才会生成
- **外观集中在一页**——设置 → 外观 现在汇总主题、字体、字体大小、震动、排序、标签与语言，每项点进去是自己的页面，主设置列表因此不再冗长
- **界面字体**——可选系统默认、**Lora** 或 **Literata**，每项用它自己的字体渲染预览；另有**字重滑轴**，每一档都对应一个真实的内置字体文件（Lora 400–700、Literata 400–900），调到哪一档就一定粗到哪一档。验证码刻意保持等宽。三款内置字体都不含汉字：中文界面一律使用系统字体，字体页会直接说明这一点（并提供跳转语言设置的入口），而不是给你一堆调了也没用的选项
- **字体大小**——主开关 + 80–200% 滑轴，叠加在系统字体缩放之上，拖动即可实时预览，并设上限以保证 PIN 键盘与账户卡片不被撑破
- **震动反馈**——主开关 + 0–100% 强度滑轴。每次点击都会给出触感（按钮、开关、设置行、PIN 键盘），滚动、拖动与侧滑返回不震。振动由应用自行驱动，因此即使系统触感反馈关闭也能生效
- **加密备份**——导出为密码保护的加密文件，可在安装兼容版本 Osmium 的设备上恢复
- **WebDAV 备份**——将加密备份上传到用户自行配置的 WebDAV 服务器（NAS、电脑或另一部手机）并从中恢复；服务器只接收密文
- **局域网快捷传输**——两台设备连接同一 Wi‑Fi 后，通过 6 位配对码端到端加密传输账户，不经过云端；接收前可预览并选择账户
- **自动备份**——按计划备份到 WebDAV 或手机的 下载/Osmium 目录；可设置间隔、时间和保留数量
- **备份结果通知**——定时备份结束后通知结果：成功为静默通知（无声音、无横幅、无角标），失败或被跳过会提醒；锁屏只显示「后台任务完成」，时间与目标需解锁后查看；可随时关闭
- **权限页面**——设置中集中列出相机、通知、生物识别、局域网、存储（Android 9 及更低）与电池优化豁免的实时状态，可直接发起申请
- **从通知进入需验证**——点备份通知进入自动备份页面时要求指纹、锁屏密码或应用 PIN（默认开启，可关闭）；该页面可修改备份密码
- **更新检查**——可选查询 GitHub Releases API；只读取公开版本信息，分两个独立通道（正式版、或正式版+预览版，两个开关默认均关闭），不发送账户或设备数据，也不会自动下载或安装
- **Steam Guard**——手动添加 Steam 账户，生成 5 位字母数字验证码
- **隐藏验证码模式**——验证码以圆点显示，复制不受影响；编辑与分享在关闭该模式前被锁定
- **排序与搜索**——支持随机、字母、添加时间、复制次数排序，并可按账户名或服务商搜索
- **可选标签**——在 设置 → 外观 → 标签 中开启标签功能；可创建多标签、筛选账户，并使用调色盘或自定义 `#RRGGBB` 颜色。没有创建标签时，主页不会显示 `Uncategorized`
- **安全门禁**——可选的打开时验证（指纹 / 系统凭据 / 应用 PIN）、自毁 PIN、默认禁止截屏
- **设备完整性检测（纯离线）**——三层证据链 + 硬件级 Key Attestation 证明（内嵌 Google 官方验证器），以 L0–L3 分级呈现，报告页逐项列出全部检查与证据；不联网、不上报。只装有 Root 管理器应用时按「提示」处理，不会判定 Root。检测到 Root 后，安全功能强制开启，局域网快捷传输、WebDAV 备份、自动备份、自毁模式设置、从第三方验证器导入五项敏感功能停用，打开应用时会提醒；可在开发者模式中解除限制。日志仅记录命中的信号名称，绝不记录密钥内容
- **开发者模式（默认隐藏；英文 / 简体中文）**——在「关于」页连点标题 7 次并通过身份验证后开启。提供设备完整性检测报告与详细检测日志、KeyStore 加密状态、解除 Root 安全限制、明文导出 / 明文导入、强制重新加密数据库、隐藏设置项、支持 4/5/7 位验证码；危险操作需身份验证 + 免责声明 + 逐字确认短语
- **时钟校准**——设备时间漂移时手动补偿 TOTP 时钟
- **10 种语言**——English、简体中文、繁體中文、Español、日本語、한국어、Deutsch、Русский、Français、हिन्दी
- **内置使用手册**（离线可用）；**用户协议与隐私政策**在你在「关于」里打开时才从官网获取最新版（获取失败时给出官网链接）

## ⚠ Steam Guard 使用前必读

手动添加 Steam 账户时，服务商（issuer）一栏必须填写 `Steam`，否则账户会被当作普通 TOTP 处理，验证码会错误。Steam Guard 验证码是 5 位字母数字组合，不是 6 位纯数字。

## 验证你的验证器

在线测试站 **https://otp.osmium.im** 支持 TOTP、HOTP 与 Steam Guard。测试过程在浏览器本地完成，不上传数据。

## 下载

最新正式版：**Osmium v2.6.2**（versionCode 821–823，按架构：821 = armeabi-v7a、822 = arm64-v8a、823 = x86_64）。请选择与设备匹配的架构：

| 文件 | 架构 | 适用设备 | 下载 |
|---|---|---|---|
| `osmium-2.6.2-arm64-v8a.apk` | arm64-v8a | 几乎所有现代手机（推荐） | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-arm64-v8a.apk) |
| `osmium-2.6.2-armeabi-v7a.apk` | armeabi-v7a | 老款 32 位手机 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-armeabi-v7a.apk) |
| `osmium-2.6.2-x86_64.apk` | x86_64 | Android 模拟器 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-x86_64.apk) |

安装与设备架构不符的包可能无法启动。三个架构使用同一签名证书，SHA-256 指纹为：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

APK 的 SHA-256 校验和见 [v2.6.2 发布说明](release-notes-v2.6.2.md) 及 Release 页面的 `SHA256SUMS.txt`。

### F-Droid

Osmium 已上架 F-Droid：**https://f-droid.org/packages/com.safekey.authenticator/**

F-Droid 用同一份源码构建、并使用同一张签名证书（配方钉死了签名密钥），所以两边是同一个应用：在两个渠道之间切换属于覆盖升级，不用卸载——对 2FA 应用来说这点很关键，卸载意味着本地账户全丢。需要注意两边版本号规则不同：GitHub 用下表里的 versionCode（2.6.2 为 821–823），F-Droid 自己推导一套（2.6.1 为 791–793），GitHub 的号更高。因此从 F-Droid 换到 GitHub 是升级；反向换回去要等 F-Droid 那边的构建追上来。

F-Droid 的索引按它自己的节奏刷新，新版本可能要几天才会出现；[包页面](https://f-droid.org/packages/com.safekey.authenticator/)上显示的就是当前真正能安装的版本——截至目前是 **2.6.1**。

## 隐私与条款

- [隐私政策](PRIVACY-zh.md)（[English](PRIVACY.md)）——官网版：[osmium.im/privacypolicy](https://osmium.im/privacypolicy/)
- [用户协议](TERMS-zh.md)（[English](TERMS.md)）——官网版：[osmium.im/useragreement](https://osmium.im/useragreement/)

应用内 设置 → 关于 展示的就是官网最新版（打开协议 / 隐私页时才去获取）。局域网传输和 WebDAV 备份均由用户主动操作触发；局域网传输使用配对码派生的端到端加密，不经过 Osmium 服务器。

## WebDAV 备份教程

各平台搭建 WebDAV 服务器的分步教程：[WEBDAV-GUIDE-zh.md](WEBDAV-GUIDE-zh.md)（[English](WEBDAV-GUIDE.md)）。WebDAV 与局域网快捷传输是两个独立功能：前者面向备份服务器，后者面向同一局域网内的另一台设备。

## 电池与后台行为

自动备份通过 Android 系统调度器执行——应用仅在备份时短暂唤醒，不驻留常驻后台服务。部分机型的省电策略可能推迟或拦截定时备份，请按 README 和应用内自动备份页面提示，为 Osmium 开启自启动及允许后台运行。从最近任务列表划掉应用，在部分系统上等同于强制停止，会清除已排程的唤醒，直到你再次手动打开应用——这与任何代码逻辑无关，请避免划掉，或为 Osmium 关闭省电限制。备份结果通知同样可能被你或系统关闭。

## 构建

```bash
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

Release 构建启用 R8 并按 ABI 分包。构建环境：Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.10、Jetpack Compose BOM 2024.09.03、`compileSdk 37`、`minSdk 26`、`targetSdk 37`。GitHub Actions 会运行单元测试并构建 Release APK；正式发布资产固定命名为 `osmium-版本号-架构.apk`。

## 内置组件

「设备完整性检测」内嵌了一个固定提交的 Google [android/keyattestation](https://github.com/android/keyattestation) 验证器源码副本（Apache-2.0，见 [keyattestation/NOTICE](keyattestation/NOTICE)），并附有明确的修改清单。其官方测试套件（`keyattestation/testdata/` 中的真机 attestation 证书链）会在 CI 中通过 `./gradlew :keyattestation:test` 运行。检测思路借鉴了社区研究（如 Duck Detector 项目）但未复用其代码；两者均在应用内「设置 → 关于 → 来源说明」中致谢。APK 内随包分发的构建期快照——`keyattestation/roots.json` 与 `app/src/main/assets/attestation_status.json`——来自 Google 公开的 attestation 端点（`android.googleapis.com/attestation/…`），由 `scripts/fetch_attestation_data.py` 在构建时尽力刷新；刷新失败时使用仓库内的上一份副本。

## 许可证

GPL-3.0-or-later，详见 [LICENSE](LICENSE) 或 [COPYING](COPYING)。

可选界面字体 **Lora** 与 **Literata** 以 SIL 开放字体许可 1.1 原样内置（仅为满足 Android 资源命名规则改为小写文件名），许可证全文随 APK 分发，并在应用内「设置 → 关于 → 来源说明」中列出；详见 [THIRD-PARTY-FONTS.md](THIRD-PARTY-FONTS.md)。
