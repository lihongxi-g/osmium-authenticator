# Osmium v2.5.0

**发布日期 / Release date: 2026-09-15**

## 中文更新说明

v2.5.0 实现应用完全开源化：二维码扫描引擎从 Google 闭源组件（ML Kit）更换为开源方案 zxing，应用不再包含任何闭源二进制；安装包体积随之减半，构建流程实现可复现。

### 变更

- **扫码引擎全开源**：二维码扫描（相机扫码、相册导入、Google 迁移导入）改用 zxing——与二维码生成同一个开源库，纯 Java、完全离线，不再依赖任何 Google 闭源组件与原生库。
- **安装包体积减半**：移除捆绑的闭源模型与原生库后，arm64-v8a 包由 10.6 MB 降至 5.1 MB，其余架构同比缩小。
- **构建可复现**：发布包可以从公开源代码逐字节重建，任何人都能验证安装包确由该源代码构建（为进入纯开源软件分发渠道做准备）。
- 版本号 **2.5.0（versionCode 60）**，可覆盖安装 v2.4.4 及更早版本。

### 说明

- 加密、存储、备份机制未改动，账户数据与全部设置原样保留。
- 与以往版本使用同一签名证书，SHA-256 指纹不变。
- 扫码行为与此前版本一致（已通过真机回归：屏幕码、纸质码、亮/暗光、相册导入）。

## English release notes

v2.5.0 makes the app fully open source: the QR-scanning engine moves from Google's closed-source ML Kit to zxing, the APK no longer bundles any closed-source binaries, the package is about half the size, and builds are now reproducible.

### Changed

- **Fully open-source scanning engine**: QR scanning (camera, gallery import, Google Authenticator migration) now uses zxing — the same open-source library already used for QR generation. Pure Java, fully offline, no closed-source components or native libraries.
- **APK size cut roughly in half**: with the bundled closed-source model and native libraries removed, the arm64-v8a package drops from 10.6 MB to 5.1 MB; the other ABIs shrink proportionally.
- **Reproducible builds**: release APKs can be rebuilt byte-for-byte from the public source, so anyone can verify that the published binaries match the code (a step toward free-software distribution channels).
- Version **2.5.0 (versionCode 60)** — installs over v2.4.4 and earlier versions.

### Notes

- Encryption, storage and backup are untouched; account data and all settings are preserved.
- Signed with the same certificate as previous releases; SHA-256 fingerprint unchanged.
- Scanning behavior is on par with previous versions (verified on-device: on-screen codes, printed codes, bright/low light, gallery import).

## SHA-256 checksums / 校验和

| File | SHA-256 |
|---|---|
| `osmium-2.5.0-arm64-v8a.apk` | `2dc5a82b6910707b86c400480a03cf9af3210269a20553a1f4a396bbd031889a` |
| `osmium-2.5.0-armeabi-v7a.apk` | `c494b7d176b609568989bdb8460706525859c3c072d48f1a97ca72223db4bdbe` |
| `osmium-2.5.0-x86_64.apk` | `ef4f89211e8d1b743931a554cc3f751123d9c55a4ac20c6b61ef24cb2c2eb8fc` |
