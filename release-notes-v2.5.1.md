# Osmium v2.5.1

**发布日期 / Release date: 2026-09-16**

## 中文更新说明

v2.5.1 是一个构建维护版本——应用本身与 v2.5.0 完全一致，没有任何功能变化。

### 变更

- **构建工具链调整**：改用默认 JDK 21 构建（所有模块统一以 Java 17 为编译目标），移除此前构建环境中的 JDK 17 变通方案；为进入 F-Droid 官方仓库做准备。
- 版本号 **2.5.1**（611 = armeabi-v7a、612 = arm64-v8a、613 = x86_64），可覆盖安装 v2.5.0 及更早版本。

### 说明

- 加密、存储、备份机制未改动，账户数据与全部设置原样保留。
- 与以往版本使用同一签名证书，SHA-256 指纹不变。
- 安装包仍可从公开源码逐字节复现构建。

## English release notes

v2.5.1 is a build-maintenance release — the app itself is identical to v2.5.0, with no functional changes.

### Changed

- **Build toolchain update**: builds now run on the default JDK 21 (all modules compile to Java 17 targets), removing the previous JDK 17 workaround from the build environment; preparing for inclusion in the F-Droid repository.
- Version **2.5.1** (611 = armeabi-v7a, 612 = arm64-v8a, 613 = x86_64) — installs over v2.5.0 and earlier.

### Notes

- Encryption, storage and backup are untouched; account data and all settings are preserved.
- Signed with the same certificate as previous releases; SHA-256 fingerprint unchanged.
- Release APKs can still be rebuilt byte-for-byte from the public source.
