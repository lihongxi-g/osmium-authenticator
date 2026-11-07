# Osmium v2.6.4

**发布日期 / Release date: 2026-11-07** · versionCode 891–893（891 = armeabi-v7a、892 = arm64-v8a、893 = x86_64）

本次是局域网快捷传输的安全加固版本，同时并入此前未发布的 2.6.2.1（标签整理）全部改动。传输协议仍是 v4，所以 2.6.2 及之后的设备照样能互传。

## 中文更新说明

### 安全：配对码不再长期有效

- 接收页面空闲 **3 分钟**后自动停止监听、作废配对码，回到需要手动刷新的状态。
- 应用切到后台（接电话、按 Home）时立即停止监听并作废当前码；回到前台需要重新显示新码，不会自己重启。
- 一次传输成功后配对码立即作废、socket 关闭，想再传必须重新取码。修掉的状态是「码的有效期 = 你什么时候离开这个页面」。

### 安全：配对码页面强制防截屏

- 配对码 / 二维码所在页面**始终**阻止截屏与录屏，不受设置里「允许截图」总开关影响——那 12 位数字就是解密 vault 的最后一把钥匙。
- 离开该页面时按你的全局设置把截屏策略恢复原状，不会顺手把其它页面的保护也关掉。

### 安全：服务端抗打断（拒绝服务加固）

- 每个连接先过闸：同时最多处理 4 个连接，同一个 IP 5 秒内只能尝试一次，超限直接断开。
- **只有证明确认过配对码的对端才消耗失败次数**。此前任何异常都计入失败，意味着陌生人连 11 次发垃圾数据就能把你这一局彻底终止——这个洞已封。
- 监听端口只绑定 Wi-Fi 网卡的地址，不再监听 0.0.0.0，VPN 与移动数据网络上的连接根本不会被接受。
- 握手的第一帧现在区分「对方是旧协议版本」和「对方在发垃圾」：只有真的版本不一致才提示「请更新」，且同一提示 10 秒内只出现一次，不再被刷屏。

### 新增：扫码配对

- 接收方在配对码下方显示二维码，内容为「地址 + 端口 + 配对码」，由发送方用相机扫描。
- 发送页新增「扫描二维码配对」：扫码成功后自动填好目标地址与配对码，仍需你点一次「连接并传输」——不会出现「拿镜头扫一下就把 vault 送出」。
- 二维码载荷严格校验：前缀、端口范围、12 位数字、以及目标必须是局域网单播地址（公网 / 组播 / 广播 / 回环一律拒绝）。任何不符合的一律当作无效码，不会发起连接。
- 手输 12 位配对码的方式保留，用于没有相机权限或对端是旧版本的情况。

### 变更：风险提示不再每次都弹窗

- 只有**真的检测到网络风险**（本机检测到的，或对方设备报告的）才会弹出确认框并要求双方确认。
- 在 Android 10 及以上，系统通常不允许应用读取本机网络设备表（`/proc/net/arp`），冒充检测因此无法运行。此前这种情况会**每次传输都弹一次**「无法检测」确认框；现在改为在传输页面固定显示一行说明文字，既不假称「已检测、无风险」，也不再打断你。
- 顺带把「检测不了」的两种原因分开说明：系统不允许读取，与设备表为空（刚连上网络还没通信过）。

### 变更：剪贴板不再自动清除配对码

- 复制配对码仍然会打上「敏感内容」标记，系统预览和输入法不会明文显示。
- 但 30 秒后自动清空的行为已移除：它在后台悄悄执行，实际最容易撞上「正要粘贴结果没了」。配对码本身在页面空闲 3 分钟后就作废，风险窗口由配对码本身控制。

### 并入 2.6.2.1（此前未发布）：标签整理

- 归类入口移到**标签页**：点开标签查看并勾选账户，账户主页不再有多选模式。
- 标签筛选行去掉「未分类」入口。
- 标签配色调整到区分度更高的色板，深色主题下能看清；编辑器会正确点亮当前颜色。
- 重名标签不再闪退（保存前查重并提示）。

### 修复

- 局域网传输的公开说明里，配对码位数写成了 6 位，实际为 12 位，已更正。
- 传输页的「已停止」「无法检测」等提示文案补齐中英双语。

## English summary

Security hardening for LAN quick transfer, plus everything from the unpublished 2.6.2.1 tag round. The transfer protocol is still v4, so devices on 2.6.2 and later keep working with this build.

- **Pairing code expires**: three minutes idle, or as soon as the app leaves the foreground, stops listening and voids the code; a successful transfer voids it immediately.
- **Screenshot protection**: the pairing page always sets `FLAG_SECURE`, even when screenshots are allowed app-wide, and restores your global policy on the way out.
- **Denial-of-service hardening**: at most four concurrent connections, one attempt per IP per five seconds, and only a peer that proved the pairing code can spend the failed-attempt budget. The listener binds to the Wi-Fi address instead of `0.0.0.0`. A junk greeting is no longer reported as a version mismatch, and the version notice is debounced.
- **QR pairing**: the receiver shows a QR code carrying address, port and code; the sender scans it and still taps Connect. The payload is validated strictly (prefix, port, 12 digits, local unicast address only). Typing the code still works.
- **Risk dialogs only on a real finding**: on Android 10+ the local device table is hidden, so the impersonation checks cannot run; that state is now a standing note on the page instead of a confirmation dialog on every transfer, and the two reasons (unreadable table / empty table) are worded separately.
- **Clipboard**: the code is still marked sensitive, but the 30-second auto-clear is gone — it ran behind the user's back and usually ate the paste it was meant to protect.
- **Also included** (unpublished 2.6.2.1): account/tag assignment moved into the tag page, the "untagged" filter entry removed, brighter tag palette, no crash on duplicate tag names.

## APK SHA-256

```
7ddac7359ac1ba90962aab1394cee4b26f1da66a17fd069e0018ce70e9b14ea4  osmium-2.6.4-arm64-v8a.apk
350469595b55edc2174b39cca42762ef65ca5b7f75900b253557510d25bfd39b  osmium-2.6.4-armeabi-v7a.apk
6ddf4f06c1e95683c3888aeab3e3592014ee7495826a62449435029362ad41dc  osmium-2.6.4-x86_64.apk
```

所有 APK 使用同一张签名证书，SHA-256 指纹：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`
