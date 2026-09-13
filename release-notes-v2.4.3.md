# Osmium v2.4.3

**发布日期 / Release date: 2026-09-13**

## 中文更新说明

v2.4.3 将设备完整性检测升级为「三层证据链 + 硬件证明」体系，并新增完整的检测报告页。

### 新功能

- **Android 设备完整性检测报告（全新，纯离线）**：
  - 三层证据链：**K0 早期快照**（启动后第一时间取样，用于检测「启动后状态漂移」）、**K1 用户态检测**（应用签名自校验、Root 管理器、su 可执行档与工具目录、可疑挂载、注入库、核心特征串、引导属性、SELinux 状态等）、**K2 一致性交叉核验**（挂载表与文件路径多来源互证，识别单一途径看不到的痕迹）。
  - **K3 硬件证明（Key Attestation）**：内嵌 Google 官方 `android/keyattestation` 验证器（固定提交、Apache-2.0），在本机验证 TEE 证书链、已验证启动链与 boot hash——这是无法伪造的硬件级证据。
  - 检测结果以 **L0–L3 分级**呈现，报告页逐项列出全部检查与证据；检测到风险（L2/L3）时，每次打开应用会弹窗提醒（带防打扰节流）。
  - **仅装有 Root 管理器应用只记为「提示」**：不会判定设备已 Root；硬件证明通过时，弱信号自动降级为提示，硬证据（如可执行的 su）永不降级。
  - 修复了在未 Root 设备上的误报问题（/data/adb 相关假阳性）。
- **开发者模式增强**：新增「检测详细日志」开关（默认关闭——检测过程不写任何日志，开启后才可见）；报告对话框显示每项检查的通过状态与原始证据。
- 提供 **x86_64（Android 模拟器）** 安装包。
- 版本号 **2.4.3（versionCode 58）**，可覆盖安装 v2.4.2 及更早版本。

### 安全说明

- 完整性检测全程在本机完成：不联网、不上报；需要联网的吊销列表/根证书更新失败时自动回退内置快照，不影响检测结果。
- Key Attestation 证书链验证使用 Google 官方验证器与其内置根证书快照。
- 任何软件检测都无法保证 100% 准确；报告用于帮助用户与开发者了解设备状态。

## English release notes

v2.4.3 upgrades device-integrity detection to a three-layer evidence chain with hardware-backed proof, and adds a full detection report.

### New

- **Device-integrity report (all-new, fully offline)**:
  - Three-layer evidence chain: **K0 boot-time snapshot** (taken immediately at launch to detect post-boot state drift), **K1 user-space checks** (app-signature self-check, root managers, su binaries and tool directories, suspicious mounts, injected libraries, kernel signatures, boot properties, SELinux state and more), and **K2 consistency cross-verification** (mount table and file paths cross-checked across vantage points to catch traces a single route cannot see).
  - **K3 hardware proof (Key Attestation)** — an embedded, pinned copy of Google's `android/keyattestation` verifier (Apache-2.0) validates the TEE certificate chain, verified boot state and boot hash entirely on-device; hardware-backed evidence that cannot be forged.
  - Results are presented as an **L0–L3 rating**; the report lists every check with its evidence. When risk is detected (L2/L3), the app shows a reminder when it opens (rate-limited).
  - **A root-manager app alone is only a hint** — it never marks the device as rooted by itself; hardware proof downgrades weak signals to hints, while hard evidence (such as a runnable su) is never downgraded.
  - Fixed a false positive on unrooted devices (the /data/adb false warning).
- **Developer-mode additions** — a “detailed detection log” toggle (off by default: nothing is logged unless enabled), and a report dialog that shows each check's status and raw evidence.
- Ships an **x86_64 (Android emulator)** APK.
- Version **2.4.3 (versionCode 58)** — installs over v2.4.2 and earlier versions.

### Security notes

- The integrity check runs entirely on-device: no network, no telemetry. If the optional revocation-list/root-certificate refresh fails, the built-in snapshot is used instead and detection is unaffected.
- Certificate-chain verification uses Google's official verifier and its bundled root-certificate snapshot.
- No software check can be perfectly accurate; the report is meant to help users and developers understand device state.
