# Osmium v2.3.9 / Osmium v2.3.9

**发布日期 / Release date: 2026-09-04**

## 中文更新说明

- 修复主页和设置页从子页面返回后滚动位置跳回顶部的问题。
- 新增“标签”二级设置页和可选的标签功能开关；关闭后主页、账户编辑页和标签管理内容不显示标签，入口仍保留以便重新开启。
- 没有创建标签时，主页顶部不显示“未分类 / Uncategorized”筛选项，也不显示空筛选条。
- 标签支持低饱和调色盘与自定义 `#RRGGBB` 颜色，旧备份中的命名颜色继续兼容。
- 局域网快捷传输已在本次版本中正式提供：同一 Wi‑Fi 下两台设备通过 6 位配对码建立临时加密连接，接收端可预览并选择导入，不经过 Osmium 云端或 WebDAV 服务器。
- GitHub 文档、应用内用户协议/隐私政策和 WebDAV 教程已同步更新。

## English release notes

- Fixed home and settings scroll positions resetting after returning from child screens.
- Added a second-level Tags settings page and optional tag switch. Disabling it hides tag UI from the home screen, account editor and tag-management content while keeping the entry available to re-enable it.
- The home screen no longer shows the Uncategorized filter or an empty filter row before any tags are created.
- Added a muted palette and custom `#RRGGBB` tag colors, while keeping named colors in older backups compatible.
- LAN quick transfer is now officially available in this release: two devices on the same Wi‑Fi establish a temporary encrypted connection with a 6-digit pairing code; the receiver can preview and selectively import accounts. No Osmium cloud or WebDAV server is involved.
- Updated GitHub documentation, in-app Terms/Privacy text and the WebDAV guide.

## 版本与资产 / Version and assets

- `versionName 2.3.9`
- `versionCode 46`
- Minimum Android: 8.0 / API 26
- Official ABI-split asset names:
  - `osmium-2.3.9-arm64-v8a.apk`
  - `osmium-2.3.9-armeabi-v7a.apk`
  - `osmium-2.3.9-x86_64.apk`

SHA-256：arm64-v8a `a8b1d159436e47dd0396770288137dcf2282aeb88ca6ef5b8016962a267a1aca`；armeabi-v7a `f501e4382575ad8f18fe0b7fdd8bc7559f5d5bbdce800752e1485a4c77fff962`；x86_64 `eaac7376f3554d41d326b3966662fdb6a9e6358b836e61e8a3862956d05fae4e`。这些值来自 GitHub Release 实际上传资产的 digest，已与对应 CI 产物重新构建得到的文件名和大小核对。

APK signing certificate SHA-256 / 签名证书 SHA-256：

`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
