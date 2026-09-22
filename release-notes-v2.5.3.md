# Osmium v2.5.3

**发布日期 / Release date: 2026-09-22**

## 中文更新说明

v2.5.3 改动了三处：**没有指纹的设备会主动提醒你设 PIN**、**局域网传输换了新协议（安全和方向都变了）**，以及若干针对 Android 17 的适配。

### 变更

- **无指纹设备的 PIN 提醒**：设备上没有录入指纹/人脸时，每次打开应用都会弹窗建议设置应用 PIN，弹窗左下角有「以后不再提醒」勾选项——不勾选就每次开 app 都提示，勾选后不再出现。此前的做法是检测到 root 时直接拦住主界面要求设 PIN，现在改为提醒，不再强制。
- **局域网传输（协议 v3）**：传输方向调整——**由发送方输入接收方屏幕上显示的验证码**，接收方不再需要输入任何东西，这样别人即使连上你的设备也无法把数据推给你。
- **配对码由 6 位改为 12 位**，并且每次传输都会用一次性的 P-256 密钥交换建立会话密钥（口令经 PBKDF2-SHA256 12 万次迭代），两台设备互相验证通过后才传输，数据用 AES-256-GCM 加密。局域网内的第三方既读不到内容，也无法冒充任何一端。
- **局域网风险检查**：打开传输页面时会静默检查当前网络（是否开放/WEP 加密、是否配置了代理、ARP 表是否有重复 MAC/IP、网关 MAC 是否在本次会话中发生变化、网关是否看起来是虚拟机/热点共享）。任一端发现风险时，**两台设备都会收到提醒**，并可以一键把对方设备「24 小时内拒绝接收/发送」。
- **黑名单加密保存**：拉黑记录用 AndroidKeyStore 密钥加密后存储，可按内网 IP、MAC 地址或设备指纹识别；被拉黑的设备仍会出现在列表里（灰色），点击可提示状态，解除拉黑需要先通过身份验证（指纹或应用 PIN）。
- **Android 17 适配**：CameraX 升级到 1.4.2，原生库的 ELF 段按 16 KB 页大小对齐；构建工具链升级到 AGP 8.5.2 / Gradle 8.7，使 APK 内的 `.so` 也按 16 KB 页对齐打包（这是 16 KB 页大小设备的安装前提）。Android 17 新增的本地网络权限会在需要时请求；应用本身仍以 targetSdk 34 运行在兼容模式，暂未提高到 37（提高会连带启用明文流量限制、预测式返回等行为变更，需要单独一轮真机验证）。

### 说明

- 版本号 **2.5.3**（631 = armeabi-v7a、632 = arm64-v8a、633 = x86_64），可覆盖安装 v2.5.2 及更早版本。
- 局域网传输的协议与旧版本不兼容：**两台设备都需要升级到 v2.5.3** 才能互传。旧版本之间仍可用旧协议互传。
- 账户数据、加密方式、备份格式均未改动，升级后数据与设置原样保留。
- 与以往版本使用同一签名证书，SHA-256 指纹不变：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。
- 安装包仍可从公开源码逐字节复现构建。

## English release notes

v2.5.3 changes three things: **devices without a fingerprint now get asked to set an app PIN**, **LAN transfer has a new protocol (both its security and its direction changed)**, and several Android 17 adaptations.

### Changed

- **PIN reminder on devices without biometrics**: when no fingerprint or face is enrolled, opening the app shows a suggestion to set an app PIN. A "don't remind me again" checkbox sits at the bottom left of the dialog — leave it unticked and the prompt returns on every app open, tick it and it stops. The previous behaviour (blocking the main screen until a PIN was set when root was detected) is replaced by this prompt.
- **LAN transfer (protocol v3)**: the direction is inverted — **the sender now types the code shown on the receiving device**. The receiver no longer types anything, so a stranger who reaches your device cannot push data onto it.
- **The pairing code is 12 digits instead of 6**, and every transfer establishes a fresh ephemeral P-256 session key (the code goes through PBKDF2-SHA256 with 120,000 iterations). Both devices authenticate each other before a single byte of data moves, and the payload is sealed with AES-256-GCM — a third party on the local network can neither read the transfer nor impersonate either end.
- **Local-network risk checks**: opening the transfer screen silently inspects the current network (open/WEP Wi-Fi, configured HTTP proxy, duplicate MAC/IP rows in the ARP table, a gateway MAC that changed during the session, a gateway that looks like a hypervisor or hotspot share). When either side finds a risk, **both devices are told**, and either one can refuse the other device for the next 24 hours.
- **Encrypted block list**: refusals are stored encrypted with an AndroidKeyStore key and matched by LAN IP, MAC address or device fingerprint. Blocked devices still appear in the list (greyed out) with a status message, and lifting a block requires an identity check (fingerprint or app PIN).
- **Android 17**: CameraX updated to 1.4.2, whose native libraries are 16 KB page-size aligned, so the app installs and runs on 16 KB-page devices; the new Android 17 local-network permission is requested where it applies. The app still runs in compatibility mode at targetSdk 34 — raising it to 37 also turns on cleartext-traffic limits and needs its own round of on-device testing.

### Notes

- Version **2.5.3** (631 = armeabi-v7a, 632 = arm64-v8a, 633 = x86_64); it installs over v2.5.2 and every earlier version.
- The new LAN transfer protocol is not compatible with older releases: **both devices must run v2.5.3** to transfer to each other. Older versions keep working with each other.
- Account data, encryption and backup formats are unchanged; your data and settings survive the update.
- Same signing certificate as every previous release; the SHA-256 fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
- Builds are still byte-for-byte reproducible from the public source.
