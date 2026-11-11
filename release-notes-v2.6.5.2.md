# Osmium v2.6.5.2

**发布日期 / Release date: 2026-11-11** · versionCode 941–943（941 = armeabi-v7a、942 = arm64-v8a、943 = x86_64）

> 本版先以预览版形式发布，同日转为正式版。装 2.6.4.1 及更早版本的用户，无论是否开启预览通道，都会收到这次更新。
>
> This build was published as a pre-release first and promoted to a stable release the same day. Users on 2.6.4.1 and older will be offered it on both channels.

## 中文更新说明

### 修复：改了自动备份时间，旧时间还在备份、新时间又备份一次

- 之前「错过就补跑」的判据是「距上次成功超过一个间隔」，跟你在设置里选的时刻无关。只要距上次备份过了 24 小时，一打开应用就立刻补跑一次——所以你每天习惯在 22:15 左右打开应用，补跑就永远落在 22:15，看起来像「改的时间没生效」。
- 现在补跑锚定到**你设置的那个时刻**：只有该时刻真的过去了、且没有成功备份，才会补跑。改设置的时间点也计入这个判断，所以把 22:15 改成 22:20 不会在 22:15 触发。
- 同时把「同一时间点触发两次就要写两个文件」的去重从「30 分钟窗口」改成**按时间槽判断**：同一个时间槽已有文件就静默跳过，旧配置留下的、卡在旧时间的触发直接判为过期丢弃。原先为了「用户刚改过设置」开的例外口子正是双份备份的来源，已删除。

### 新增：更新检查多了一个不依赖 GitHub 的来源

- 每个更新通道现在查两个相互独立的来源：GitHub Releases API，以及官网 osmium.im 上的公开版本文件（正式版用 `release.json`，预览版用 `release-preview.json`）。
- 规则：任一来源发现新版本就提示更新；只要有一个来源正常应答且没有新版本，就保持静默；**只有所有来源都失败**才提示「检查更新失败」。这样 api.github.com 被拦截或限流时，不会把好好的网络误报成「检查失败」，预览版也不会再因为 GitHub 连不上而查不到。
- 检查失败现在会如实提示，并写入带 HTTP 状态码的日志（只记域名，不含任何可识别信息），导出日志即可判断是 403 限流还是连不上。

### 变更：提示文案不再写死 GitHub

- 「发现新版本」提示里的「前往 GitHub」改成「前往下载页」：正式版更新会打开官网下载页（GitHub 打不开时也能用），预览版更新才回到对应的 GitHub Release。

### 变更：移除 Lora 字体，只保留 Literata

- 界面可选字体从两款减到一款（Literata），安装包内字体文件从 10 个降到 6 个。之前选过 Lora 的安装会继续显示 Literata，不会掉回系统字体。

### 其他

- 构建工具链跟进最新稳定版（Gradle / AGP / compileSdk 37），补充 BouncyCastle 1.86 的许可证与署名。界面无变化。

## English notes

### Fixed: changing the backup time still backed up at the old time, and again at the new one

- The catch-up rule was "more than one interval since the last success", with no relation to the time of day you picked. Once 24 hours had passed, opening the app started a backup immediately — so opening the app around 22:15 every day kept producing 22:15 backups, which looked like the new time never took effect.
- The catch-up is now anchored to the configured time of day: it runs only once that slot has passed without a successful backup. The moment you change the setting counts too, so moving 22:15 to 22:20 no longer produces anything at 22:15.
- The duplicate-trigger guard changed from a 30-minute window to **slot membership**: a slot that already produced a file is skipped silently, and a trigger armed for a time you have since changed is dropped as stale. The "the user just changed the schedule" escape hatch that let the old guard through was itself the source of the second file and is gone.

### New: the update check no longer depends on GitHub alone

- Each update channel now asks two independent sources: the GitHub Releases API and the public version files on osmium.im (`release.json` for stable releases, `release-preview.json` for previews).
- Rules: any source that finds a newer build raises the prompt; if at least one source answers and none has a newer build, the check stays silent; only when **every** source fails does the app say the check failed. A blocked or rate-limited api.github.com therefore no longer turns a working connection into a failed check, and the preview channel keeps working where GitHub is unreachable.
- A failed check is now reported instead of looking identical to "you are up to date", and it logs the HTTP status (host only, nothing identifying) so an exported log tells a 403 from a timeout.

### Changed: the notice no longer promises GitHub

- "Open GitHub" became "Open download page": a stable update opens the site's download section (which also works where GitHub does not), while a preview update opens its GitHub release.

### Changed: the Lora face is gone, Literata stays

- The optional interface font list is down to one face (Literata) and the APK ships 6 font files instead of 10. Installs that had picked Lora keep rendering Literata rather than falling back to the system font.

### Other

- The build toolchain moved to the latest stable versions (Gradle, AGP, compileSdk 37) and BouncyCastle 1.86 is credited with its licence. No visible UI change.

## APK SHA-256

```
c89108fbea39492243002272560895e67fa75ae98ca7b4c8bfff19efb14bb01c  osmium-2.6.5.2-arm64-v8a.apk
8d14cc23ab0184746e1b9bc2f10f176d316be818973a891c79a7a2cbfb117f83  osmium-2.6.5.2-armeabi-v7a.apk
a64070b0a4d619c62acfe629b0df91c97c339974852134ea8d7796ceeb3739de  osmium-2.6.5.2-x86_64.apk
```
