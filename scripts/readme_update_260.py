#!/usr/bin/env python3
"""Bring both READMEs to v2.6.0: download table, feature list, notifications note."""
import io
import pathlib
import re

R = pathlib.Path("/root/osmium-inspect")

ZH_DL = """最新正式版：**Osmium v2.6.0**（versionCode 771–773，按架构：771 = armeabi-v7a、772 = arm64-v8a、773 = x86_64）。请选择与设备匹配的架构：

| 文件 | 架构 | 适用设备 | 下载 |
|---|---|---|---|
| `osmium-2.6.0-arm64-v8a.apk` | arm64-v8a | 几乎所有现代手机（推荐） | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-arm64-v8a.apk) |
| `osmium-2.6.0-armeabi-v7a.apk` | armeabi-v7a | 老款 32 位手机 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-armeabi-v7a.apk) |
| `osmium-2.6.0-x86_64.apk` | x86_64 | Android 模拟器 | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-x86_64.apk) |"""

EN_DL = """Latest stable release: **Osmium v2.6.0** (`versionCode 771-773, per ABI`: 771 = armeabi-v7a, 772 = arm64-v8a, 773 = x86_64). Choose the ABI matching your device:

| File | Architecture | Devices | Download |
|---|---|---|---|
| `osmium-2.6.0-arm64-v8a.apk` | arm64-v8a | Virtually all modern phones (recommended) | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-arm64-v8a.apk) |
| `osmium-2.6.0-armeabi-v7a.apk` | armeabi-v7a | Older 32-bit phones | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-armeabi-v7a.apk) |
| `osmium-2.6.0-x86_64.apk` | x86_64 | Android emulators | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.0/osmium-2.6.0-x86_64.apk) |"""

ZH_NEW_FEATURES = """- **备份结果通知**——定时备份结束后通知结果：成功为静默通知（无声音、无横幅、无角标），失败或被跳过会提醒；锁屏只显示「后台任务完成」，时间与目标需解锁后查看；可随时关闭
- **权限页面**——设置中集中列出相机、通知、生物识别、局域网、存储（Android 9 及更低）与电池优化豁免的实时状态，可直接发起申请
- **从通知进入需验证**——点备份通知进入自动备份页面时要求指纹、锁屏密码或应用 PIN（默认开启，可关闭）；该页面可修改备份密码
"""

EN_NEW_FEATURES = """- **Backup result notifications** — scheduled runs report their outcome: success is silent (no sound, no banner, no badge), failure or a skipped run alerts; the lock screen shows only that a background task finished, with time and destination revealed after unlocking; can be switched off at any time
- **Permissions page** — one place under Settings listing live status for camera, notifications, biometrics, local network, storage (Android 9 and older) and the battery-optimisation exemption, with the request one tap away
- **Verification when opening from a notification** — opening the auto-backup page from a backup notification asks for the fingerprint, screen lock or app PIN (on by default, can be switched off); that page can change the backup password
"""


def sub_once(text: str, old: str, new: str, what: str) -> str:
    if old not in text:
        raise SystemExit(f"{what}: pattern not found")
    return text.replace(old, new, 1)


def main() -> int:
    # ---- Chinese README
    p = R / "README-zh.md"
    t = p.read_text(encoding="utf-8")
    t = re.sub(
        r"最新正式版：\*\*Osmium v2\.5\.\d+\*\*.*?\| `osmium-2\.5\.\d+-x86_64\.apk`[^\n]*\n?",
        ZH_DL + "\n",
        t,
        count=1,
        flags=re.S,
    )
    t = sub_once(
        t,
        "- **自动备份**——按计划备份到 WebDAV 或手机的 下载/Osmium 目录；可设置间隔、时间和保留数量\n",
        "- **自动备份**——按计划备份到 WebDAV 或手机的 下载/Osmium 目录；可设置间隔、时间和保留数量\n" + ZH_NEW_FEATURES,
        "zh feature list",
    )
    t = re.sub(r"\[v2\.5\.\d+ 发布说明\]\(release-notes-v2\.5\.\d+\.md\)", "[v2.6.0 发布说明](release-notes-v2.6.0.md)", t, count=1)
    t = sub_once(
        t,
        "部分机型的省电策略可能推迟或拦截定时备份，请按 README 和应用内自动备份页面提示，为 Osmium 开启自启动及允许后台运行。",
        "部分机型的省电策略可能推迟或拦截定时备份，请按 README 和应用内自动备份页面提示，为 Osmium 开启自启动及允许后台运行。从最近任务列表划掉应用，在部分系统上等同于强制停止，会清除已排程的唤醒，直到你再次手动打开应用——这与任何代码逻辑无关，请避免划掉，或为 Osmium 关闭省电限制。备份结果通知同样可能被你或系统关闭。",
        "zh background note",
    )
    p.write_text(t, encoding="utf-8")
    print("README-zh.md updated")

    # ---- English README
    p = R / "README.md"
    t = p.read_text(encoding="utf-8")
    t = re.sub(
        r"Latest stable release: \*\*Osmium v2\.5\.\d+\*\*.*?\| `osmium-2\.5\.\d+-x86_64\.apk`[^\n]*\n?",
        EN_DL + "\n",
        t,
        count=1,
        flags=re.S,
    )
    t = sub_once(
        t,
        "- **Automatic backup** — schedule backups to WebDAV or the phone\u2019s Download/Osmium folder, with configurable interval, time and retention\n",
        "- **Automatic backup** — schedule backups to WebDAV or the phone\u2019s Download/Osmium folder, with configurable interval, time and retention\n" + EN_NEW_FEATURES,
        "en feature list",
    )
    t = re.sub(r"\[v2\.5\.\d+ release notes\]\(release-notes-v2\.5\.\d+\.md\)", "[v2.6.0 release notes](release-notes-v2.6.0.md)", t, count=1)
    t = sub_once(
        t,
        "See the in-app Auto backup page for the permission guidance.",
        "See the in-app Auto backup page for the permission guidance. Note that swiping the app away from the recents list counts as a force-stop on some systems and clears scheduled wake-ups until you open the app again — no application logic can override that, so avoid swiping it away or remove the battery restriction for Osmium. Backup result notifications can likewise be switched off by you or by the system.",
        "en background note",
    )
    p.write_text(t, encoding="utf-8")
    print("README.md updated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
