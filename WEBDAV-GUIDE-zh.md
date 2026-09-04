# Osmium / WebDAV 备份教程

此目录中的文档统一说明 Osmium v2.3.9 的 WebDAV 备份与恢复。WebDAV 备份和局域网快捷传输是两个独立功能：前者连接用户配置的备份服务器，后者连接同一 Wi‑Fi 内的另一台设备。

## Osmium WebDAV 客户端需要什么

Osmium 会使用以下 WebDAV 方法：

- `PROPFIND`：测试连接、列出备份
- `MKCOL`：创建备份目录
- `PUT`：上传加密备份
- `GET`：下载备份
- `DELETE`：在“管理备份”中删除旧备份

服务器需要支持 HTTP Basic 认证；匿名服务器也可以使用。备份在设备本地先经 PBKDF2 + AES-256-GCM 加密，服务器只收到密文。

如果地址使用 `http://`，应用保存前会要求屏幕确认；实际部署仍建议使用 HTTPS。设备不信任的自签名证书会被拒绝。

## NAS

### 群晖 Synology DSM

1. 套件中心安装 **WebDAV Server**。
2. 开启 HTTP（通常 5005）或 HTTPS（通常 5006）。
3. 给专用共享文件夹配置读写权限。
4. 在 Osmium 中填写 `http://NAS-IP:5005` 或对应 HTTPS 地址，并输入账号密码。

### 威联通 QNAP

控制台 → 应用程序 → Web 服务器 → WebDAV，启用服务并配置共享文件夹读写权限，然后填写 `http://NAS-IP:端口`。

### 极空间

系统设置 → 文件及共享服务 → WebDAV 服务 → 开启。建议使用最小权限的专用账号。

### 绿联 UGREEN UGOS Pro

控制面板 → 文件服务 → WebDAV → 启用服务 → 应用，按需配置端口与权限。

### TrueNAS

- SCALE：Shares → WebDAV 小组件 → Add。
- CORE：Sharing → WebDAV Shares → Add，选择数据集。

## Linux / macOS / Windows

### Docker（Linux 或 Docker Desktop）

```bash
docker run --restart unless-stopped \
  -v /data/osmium-backups:/var/lib/dav/data \
  -e AUTH_TYPE=Basic -e USERNAME=osmium -e PASSWORD=你的密码 \
  -p 8080:80 -d bytemark/webdav
```

Osmium 地址：`http://服务器IP:8080`。

### rclone

```bash
curl https://rclone.org/install.sh | sudo bash
rclone serve webdav /data/osmium-backups \
  --addr :8080 --user osmium --pass 你的密码
```

### Apache mod_dav

```bash
sudo apt install apache2
sudo a2enmod dav dav_fs
sudo systemctl restart apache2
```

Apache 站点中启用 `Dav On`、Basic 认证，并授予目标目录读写权限。

### Windows IIS

在“服务器管理器”中安装 WebDAV Publishing 与基本身份验证，创建专用目录并配置授权规则。Windows 防火墙只放行你实际使用的端口。

### macOS

macOS 没有适合作为长期服务端的原生 WebDAV 服务。使用 Docker 或 rclone；Finder 的“连接服务器”是客户端，不会提供 WebDAV 服务。

### 不推荐：nginx 原生 DAV 模块

nginx 的 `ngx_http_dav_module` 通常不支持 `PROPFIND`。上传可能看似成功，但 Osmium 的测试连接、列出备份和恢复会失败。请使用完整 WebDAV 服务端。

## Android 手机临时服务

手机作为服务端只建议临时使用或测试。电池策略可能终止后台进程，切换 Wi‑Fi 后 IP 也可能变化。

### Termux + rclone

```bash
pkg update && pkg install rclone
mkdir -p ~/backups
rclone serve webdav ~/backups --addr :8080 --user osmium --pass 你的密码
```

允许 Termux 后台运行并关闭其电池优化，在 Osmium 中填写手机局域网 IP。

### MiXplorer

打开 MiXplorer → 菜单 → 服务器 → 启动 WebDAV 服务，使用界面显示的地址。

## Osmium 端操作

1. 设置 → 数据 → **WebDAV 备份**。
2. 填写服务器地址、用户名和密码。
3. 点击**测试连接**，确认 `PROPFIND` 成功。
4. 点击**保存**。
5. 备份或恢复时输入独立的备份密码；它不是 WebDAV 登录密码。
6. “管理备份”可列出并删除服务器旧文件，删除前确认目标。

## 局域网快捷传输不是 WebDAV

从旧手机迁移到新手机时，也可以直接使用设置中的**局域网快捷传输**：

1. 两台设备连接同一 Wi‑Fi。
2. 发送端打开传输页并显示配对码。
3. 接收端自动发现发送端，或手动填写 IP/端口。
4. 输入 6 位配对码，预览并选择需要导入的账户。

传输使用配对码派生的 AES-256-GCM 临时加密，不经过 Osmium 云端或 WebDAV 服务器。不要向不信任的人提供配对码。
