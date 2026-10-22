# Osmium v2.6.0

**发布日期 / Release date: 2026-10-22**

本次更新为自动备份增加了结果通知与一套独立的权限管理页面，并修复了自动备份与通知链路上的若干可靠性问题。

## 中文更新说明

### 新增：备份结果通知

- 无人值守的自动备份（定时闹钟、系统作业、启动补跑）结束后会发送一条通知，说明结果与备份文件名。
- 通知分为两类通道：**备份成功**为静默通知——不出声、不振动、不亮屏、无角标，凌晨执行也不会打扰；**备份失败或被跳过**为普通通知，会响一声并弹出横幅，因为「没有做成」不该被忽略。
- 隐私保护：锁屏上只显示「Osmium：后台任务完成」，不显示时间、目标服务器或失败原因，正文内容仅在解锁后可见。
- 手动「立即备份」不发送通知——结果直接显示在页面上。
- 点击通知会打开自动备份页面。

### 新增：从通知进入需要验证（可关闭）

- 自动备份页面可以修改备份密码，因此在未开启「打开时验证」的设备上，任何拿到已解锁手机的人都能通过通知进入该页面。
- 本次新增开关（位于自动备份页面，默认开启）：从通知进入该页面时，要求指纹、锁屏密码或应用 PIN——三者按设备实际可用情况显示，与设置页中受保护条目的验证方式一致。
- 已开启「打开时验证」时不再重复要求：该次进入已经完成身份验证。
- 取消验证不会打开页面。

### 新增：权限页面

- 设置中新增独立页面，集中列出应用会申请的全部权限与特殊访问：相机、通知、生物识别、局域网（Android 17+）、存储（Android 9 及更低版本）以及电池优化豁免。
- 每项均显示实时状态：已授予显示绿色并不可点击；未授予可点击申请；当前系统版本不需要的项目直接说明，而不是长期显示「未授予」。
- 从系统对话框返回时状态自动刷新。

### 修复

- **点击备份通知会闪退**：应用在进程处于活跃状态时创建主界面状态对象，会读取到尚未初始化的随机排序缓存，导致空指针崩溃。已将相关状态提前初始化。这也是此前「点通知进不去自动备份页面」的真正原因。
- **修改备份时间后没有反应**：为避免闹钟与系统作业重复执行，此前设有 30 分钟的重复触发抑制窗口，但该窗口也会吞掉用户刚刚设定的新时间——触发被静默跳过，既不发送通知也不更新状态。现在只要改动过计划设置，就不再被该窗口抑制。
- **备份成功通知看不见**：成功通知此前使用低重要性通道，部分系统会将其归入「静默通知」折叠组。已改用新的通知通道（默认重要性、关闭声音），既不打扰也能正常显示。
- **诊断日志在进程结束后丢失**：日志此前仅保存在内存中，应用被系统结束后，后台发生的自动备份过程没有任何痕迹。现在日志同时写入应用私有目录的滚动文件，导出的日志包含应用关闭期间执行的记录。
- **权限页「已授予」显示为黄色**：状态文字此前使用主题第三色，在动态取色下会跟随壁纸变成黄色，容易被误读为警告。现改为固定成功绿色（浅色 / 深色主题各一档）。
- 计划与补跑判断新增日志记录，便于从导出日志中区分「未排程」「已排程但未触发」「已触发但被抑制」。

### 其他

- 新增界面文案 22 条，11 种语言全部同步。
- 通知权限（Android 13+）在开启通知开关时按需申请；若用户此前拒绝，页面会给出前往系统设置的入口。
- 未引入任何新的网络请求、第三方 SDK 或数据收集。

## English release notes

This release adds result notifications for the automatic backup and a dedicated permissions page, and fixes several reliability issues in the backup and notification paths.

### New: backup result notifications

- Unattended runs (scheduled alarm, system job, catch-up on launch) now post a notification with the outcome and the file name.
- Two channels by design: **success** is silent — no sound, no vibration, no screen wake, no badge, so a 03:00 run cannot disturb anyone; **failure or skipped** uses normal priority and does alert, because a backup that did not happen must not go unnoticed.
- Privacy: the lock screen only shows "Osmium: background task finished". Time, destination and failure reason are revealed after unlocking.
- Manual "Back up now" never notifies — the result is shown on the page itself.
- Tapping the notification opens the automatic backup page.

### New: optional verification when opening from a notification

- The automatic backup page can change the backup password, so on a device without "verify on open" anyone holding the unlocked phone could reach it through the notification.
- New switch on that page (on by default): opening the page from a notification asks for the fingerprint, the screen lock or the app PIN — whichever this device can actually do, matching the verification the settings screen already requires for its protected entries.
- Skipped while "verify on open" is enabled: that gate has already verified the same entry.
- Cancelling the check does not open the page.

### New: permissions page

- A dedicated page under Settings lists every permission and special access the app can ask for: camera, notifications, biometrics, local network (Android 17+), storage (Android 9 and older) and the battery-optimisation exemption.
- Each row shows live status: granted rows turn green and are no longer clickable, ungranted rows ask, and rows this OS version does not need say so instead of showing a permanent "denied".
- Statuses refresh when returning from a system dialog.

### Fixes

- **Tapping the backup notification crashed the app**: when the process was already warm, creating the main view state read a random-sort cache that had not been initialised yet, which threw a null pointer exception. The state is now initialised before any of it can be read. This was the real cause of "the notification does not open the backup page".
- **Changing the backup time did nothing**: a 30-minute duplicate-trigger window (there to stop the alarm and the system job writing two files for one slot) also absorbed the run the user had just asked for. Such triggers were skipped silently — no notification, no status change. A schedule change now defeats that window.
- **Success notifications were invisible**: the success channel used low importance, which some OEM builds fold into their silent notification group. It now uses a new channel (default importance, sound removed) that is quiet yet actually shown.
- **Diagnostic logs died with the process**: the log lived in memory only, so anything the scheduled path did while the app was closed left no trace. Logs are now also appended to a rolling file in app-private storage, and the exported log includes runs that happened while the app was closed.
- **"Granted" was rendered in yellow** on the permissions page: it used the theme's tertiary colour, which follows the wallpaper under dynamic colour and reads like a warning. It now uses a fixed success green, with a light and a dark tone.
- Scheduling and catch-up decisions are now logged, so an exported log distinguishes "never scheduled" from "scheduled but never fired" and "fired but suppressed".

### Other

- 22 new UI strings, added in all 11 languages.
- The notification permission (Android 13+) is requested when the notification switch is turned on; if it was denied earlier, the page offers a shortcut to the system settings.
- No new network requests, no third-party SDKs, no data collection.

## 安装 / Install

versionName **2.6.0**，versionCode **77**（771 = armeabi-v7a、772 = arm64-v8a、773 = x86_64）。覆盖安装即可，账户与设置原样保留。

签名证书指纹不变（SHA-256）：`B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`。

versionCode 77 (771 = armeabi-v7a, 772 = arm64-v8a, 773 = x86_64). Installs over the previous version and keeps your accounts and settings. Unchanged signing certificate, SHA-256 `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
