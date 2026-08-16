# Osmium WebDAV 备份教程

本教程说明如何搭建 WebDAV 服务器，用于 Osmium v2.3.0 的局域网备份与恢复功能。

## 支持情况总览

| 平台 | 支持情况 |
|---|---|
| NAS（群晖 / 威联通 / 极空间 / 绿联 UGOS Pro / TrueNAS） | ✅ 内置 WebDAV 服务 |
| Linux 服务器 / 电脑 | ✅ 多种方案（Docker / rclone / Python / Apache） |
| Android 手机 | ✅ 可作临时服务器（Termux / MiXplorer） |
| macOS | ✅ 无原生服务器，可用 rclone / Docker |
| Windows | ✅ IIS WebDAV / Docker / rclone |
| iOS / iPadOS | ⚠️ 无原生服务器，第三方应用受后台限制，不建议 |
| HarmonyOS 5.0+ | ❓ 未知，需自行尝试（见下文说明） |

## Osmium 需要什么

Osmium 的 WebDAV 客户端使用四个 HTTP 请求：`PROPFIND`（测试连接、列出备份）、`MKCOL`（创建目录）、`PUT`（上传备份）、`GET`（下载备份）。服务器需要支持 **HTTP Basic 认证**（匿名也可，Osmium 用户名可留空）。

**重要**：部分 WebDAV 实现缺少 PROPFIND 支持（例如 nginx 的原生 dav 模块只支持 PUT/DELETE/MKCOL/COPY/MOVE）。这类服务器可以上传备份，但 Osmium 的「测试连接」和「恢复（列出备份）」会失败。请使用下方列出的方案。

备份文件在上传前已在手机内完成端到端加密（PBKDF2 + AES-256-GCM），服务器上只存储密文，因此局域网内使用明文 HTTP 传输是安全的。HTTPS 则必须使用设备信任的证书——**Osmium 会拒绝自签名证书**（安全设计，不会放宽）。

## NAS

### 群晖（Synology DSM）

1. 套件中心 → 搜索安装 **WebDAV Server**
2. 打开 WebDAV Server → 勾选 **启用 HTTP**（默认端口 5005）或 **启用 HTTPS**（默认 5006）
3. 设置共享文件夹权限（Osmium 只需要一个文件夹的读写权限）
4. Osmium 中填写：`http://群晖IP:5005`，用户名为 DSM 账户，密码为对应密码

官方文档：Synology 知识中心「WebDAV Server」。

### 威联通（QNAP QTS/QuTS）

1. 控制台 → 应用程序 → **Web 服务器** → **WebDAV** → 选择 **启用 WebDAV**
2. 按需设置端口与共享文件夹权限
3. Osmium 中填写 `http://NAS的IP:端口`

官方文档：docs.qnap.com「Enabling WebDAV」。

### 极空间（ZSpace）

1. 系统设置 → **文件及共享服务** → **WebDAV 服务** → 开启
2. 默认 HTTP 端口为 **5005**（可在页面修改；HTTPS 端口需自行配置证书）
3. 建议在 WebDAV 服务页**新增一个最小权限的独立账号**给 Osmium 使用
4. Osmium 中填写 `http://极空间IP:5005`

### 绿联（UGREEN UGOS Pro）

UGOS Pro 系统内置 WebDAV 服务（官方教程确认）：

1. 打开 **控制面板** → **文件服务** → **WebDAV**
2. 勾选「**启用 WebDAV 服务**」→ 点击「**应用**」保存
3. 按需设置端口与账号权限
4. Osmium 中填写 `http://绿联NAS的IP:端口`

官方文档：绿联官网教程「如何使用 WebDAV 访问绿联 NAS 上的文件」（ugnas.com/tutorial-detail/id-223.html）。

### TrueNAS（CORE / SCALE）

- **SCALE**：共享（Shares）→ WebDAV 小组件 → **添加**（Add）
- **CORE**：共享（Sharing）→ **WebDAV 共享** → **添加**（ADD），选择数据集
- 启用后 Osmium 中填写 `http://TrueNAS的IP:端口`

官方文档：truenas.com/docs「Configuring WebDAV Shares」（SCALE）/「WebDav Share Creation」（CORE）。

### 其他支持 Docker 的 NAS

任何支持 Docker 的 NAS（群晖 / 威联通 / 绿联部分型号 / OMV 等）都可以用通用 Docker 方案，见下文 Linux 一节。

## Linux（服务器 / 电脑）

### 方案 A：Docker（推荐，一条命令）

```bash
docker run --restart unless-stopped \
  -v /data/osmium-backups:/var/lib/dav/data \
  -e AUTH_TYPE=Basic -e USERNAME=osmium -e PASSWORD=你的密码 \
  -p 8080:80 -d bytemark/webdav
```

- 镜像：`bytemark/webdav`（基于 Apache WebDAV，完整支持 PROPFIND）
- Osmium 中填写 `http://服务器IP:8080`
- 备份文件存放在宿主机 `/data/osmium-backups` 目录

### 方案 B：rclone（单文件工具，最轻量）

```bash
# 安装（任一方式）：官方脚本
curl https://rclone.org/install.sh | sudo bash
# 启动服务（前台；后台运行可自行加 systemd 服务）
rclone serve webdav /data/osmium-backups \
  --addr :8080 --user osmium --pass 你的密码
```

官方文档：rclone.org/commands/rclone_serve_webdav。

### 方案 C：Python wsgidav（pip 安装）

```bash
pip install wsgidav cheroot
```

配置 `wsgidav.yaml`：

```yaml
host: 0.0.0.0
port: 8080
provider_mapping:
  "/": "/data/osmium-backups"
auth:
  anonymous: false
simple_dc:
  user_mapping:
    "*":
      "osmium":
        password: "你的密码"
http_authenticator:
  accept_basic: true
```

```bash
wsgidav --config wsgidav.yaml
```

（此方案已与 Osmium 实测兼容。）

### 方案 D：Apache mod_dav

```bash
sudo apt install apache2
sudo a2enmod dav dav_fs
# 在站点配置中添加 DAV 目录，例如：
# Alias /osmium /data/osmium-backups
# <Directory /data/osmium-backups>
#   Dav On
#   AuthType Basic
#   AuthName "Osmium"
#   AuthUserFile /etc/apache2/.htpasswd
#   Require valid-user
# </Directory>
sudo systemctl restart apache2
```

Osmium 中填写 `http://服务器IP/osmium`（注意带上路径）。

### ⚠️ 不兼容：nginx 原生 dav 模块

nginx 的 `ngx_http_dav_module` 仅支持 PUT/DELETE/MKCOL/COPY/MOVE，**不支持 PROPFIND**。用它搭建的服务器只能上传备份，Osmium 的测试连接与恢复功能都会失败。请改用上述任一方案。

## Android（手机）

> 手机作服务器仅建议临时使用或测试：电池策略可能终止后台进程，IP 也会随网络变化。

### 方案 A：Termux + rclone（已实测）

```bash
pkg update && pkg install rclone
mkdir -p ~/backups
rclone serve webdav ~/backups --addr :8080 --user osmium --pass 你的密码
```

- 查 IP：系统设置 → WLAN → 当前 WiFi 详情
- 保活：安装 Termux:API 后执行 `termux-wake-lock`；并在系统设置中允许 Termux 后台运行 / 电池不优化
- Osmium 中填写 `http://手机IP:8080`
- 备份文件在 Termux 内可用 `ls ~/backups/osmium/` 查看（卸载 Termux 会丢失备份）

### 方案 B：MiXplorer

1. 打开 MiXplorer → 右上角菜单 → **服务器** → 启动 **WebDAV 服务器**
2. 界面会显示地址（如 `http://192.168.1.x:8080`）
3. Osmium 中填写该地址（用户名为空即可）

## macOS

macOS 没有内置 WebDAV 服务器（Finder 的「连接服务器」是客户端功能，不能作为服务器）。

- **方案 A**：`brew install rclone`，然后 `rclone serve webdav /Users/你/backups --addr :8080 --user osmium --pass 你的密码`
- **方案 B**：安装 Docker Desktop，使用上方 Linux → Docker 方案
- Osmium 中填写 `http://Mac的IP:8080`

## Windows

### 方案 A：IIS WebDAV（系统内置）

1. 控制面板 → 程序和功能 → 启用或关闭 Windows 功能 → 展开 **Internet Information Services** → 万维网服务 → 常见 HTTP 功能 → 勾选 **WebDAV 发布**
2. 打开 **IIS 管理器** → 网站（或新建网站）→ 右键 → **添加 WebDAV 创作规则** → 允许指定用户/全部用户读写
3. 开启 **WebDAV 创作**功能
4. Windows 防火墙放行 80 端口
5. Osmium 中填写 `http://电脑IP`（若发布在子目录则带上路径）

### 方案 B：Docker Desktop

安装 Docker Desktop 后，使用上方 Linux → Docker 方案（`-p 8080:80`）。

### 方案 C：rclone

```powershell
winget install Rclone.Rclone
rclone serve webdav C:\osmium-backups --addr :8080 --user osmium --pass 你的密码
```

Osmium 中填写 `http://电脑IP:8080`。

## iOS / iPadOS

iOS 没有内置 WebDAV 服务器；App Store 存在少量第三方 WebDAV 服务器应用，但 iOS 的后台挂起机制会在应用切到后台后终止服务，**不适合作为备份服务器**。建议使用 NAS 或电脑作为备份目标。iOS 设备本身也无法安装 Osmium（Android 应用）。

## HarmonyOS 5.0+（NEXT）

- Osmium 是 Android 应用，HarmonyOS NEXT 不原生兼容 Android 应用；如需在鸿蒙设备运行 Osmium，请尝试华为官方 Android 兼容工具（如卓易通），兼容性未经验证
- WebDAV 服务器方案：**未知，需自行尝试**。经检索未发现鸿蒙 NEXT 应用市场中有现成的 WebDAV 服务器应用；可在应用市场自行搜索「WebDAV 服务器」，或在支持 Docker 的设备上使用上方 Docker 方案

## Osmium 端操作（通用）

注意：进入 WebDAV 备份页面需要先通过指纹 / 系统密码 / Osmium PIN 之一验证身份。

1. 设置 → 数据 → **WebDAV 备份**
2. **服务器地址**：`http://IP:端口`，支持主机名（如 `http://nas.local:5005`）；若服务器发布在子路径（如 `/webdav`），填写完整路径
3. **用户名 / 密码**：服务器账号；匿名服务器可留空
4. 点「**测试连接**」确认连通，点「**保存**」记住配置（密码加密存储在本机）
5. 「**立即备份**」→ 设置备份密码（≥8 位）→ 上传。该密码与服务器密码无关，**忘记则所有备份无法恢复**
6. 「**恢复…**」→ 输入备份密码 → 从列表选择备份 → 勾选账户 → 确认导入
7. 「**管理备份**」列出服务器上的备份；点垃圾桶图标可删除旧备份（需确认，不可撤销）

**建议**：在路由器中为服务器设置静态租约（固定 IP），或使用主机名，避免 IP 变化后备份失败。
