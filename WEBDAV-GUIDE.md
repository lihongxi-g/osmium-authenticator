# Osmium WebDAV Backup Guide

How to set up a WebDAV server for Osmium v2.3.0's local-network backup and restore feature.

## Support overview

| Platform | Status |
|---|---|
| NAS (Synology / QNAP / ZSpace / TrueNAS) | ✅ Built-in WebDAV service |
| NAS (UGREEN UGOS) | ❓ Unknown — try it yourself (no official docs found) |
| Linux server / PC | ✅ Several options (Docker / rclone / Python / Apache) |
| Android phone | ✅ As a temporary server (Termux / MiXplorer) |
| macOS | ✅ No native server; use rclone / Docker |
| Windows | ✅ IIS WebDAV / Docker / rclone |
| iOS / iPadOS | ⚠️ No native server; third-party apps are unreliable in the background — not recommended |
| HarmonyOS 5.0+ | ❓ Unknown — try it yourself (see below) |

## What Osmium needs

Osmium's WebDAV client uses four HTTP methods: `PROPFIND` (test connection, list backups), `MKCOL` (create directory), `PUT` (upload backup), `GET` (download backup). The server must support **HTTP Basic authentication** (anonymous is fine too — the username can be left blank in Osmium).

**Important**: some WebDAV implementations lack PROPFIND support (e.g. nginx's built-in dav module only supports PUT/DELETE/MKCOL/COPY/MOVE). Such servers can receive uploads, but Osmium's "Test connection" and "Restore" (listing backups) will fail. Use one of the options below.

Backups are end-to-end encrypted on the phone before upload (PBKDF2 + AES-256-GCM) — the server only ever stores ciphertext, so plain HTTP on your LAN is acceptable. HTTPS requires a certificate the device trusts — **Osmium rejects self-signed certificates** (deliberate security design).

## NAS

### Synology (DSM)

1. Package Center → install **WebDAV Server**
2. Open WebDAV Server → tick **Enable HTTP** (default port 5005) or **Enable HTTPS** (default 5006)
3. Grant shared-folder permissions (Osmium needs read/write on one folder)
4. In Osmium: `http://<NAS-IP>:5005`, username = DSM account, password = its password

Official docs: Synology Knowledge Center, "WebDAV Server".

### QNAP (QTS / QuTS)

1. Control Panel → Applications → **Web Server** → **WebDAV** → select **Enable WebDAV**
2. Configure port and shared-folder permissions as needed
3. In Osmium: `http://<NAS-IP>:<port>`

Official docs: docs.qnap.com, "Enabling WebDAV".

### ZSpace (极空间)

1. System Settings → **File & Sharing Services** → **WebDAV Service** → enable
2. Default HTTP port is **5005** (changeable; HTTPS requires configuring a certificate)
3. Recommended: create a **least-privilege dedicated account** for Osmium on the WebDAV page
4. In Osmium: `http://<ZSpace-IP>:5005`

### UGREEN (UGOS)

No official documentation on WebDAV support was found. **Unknown — try it yourself**: look for "File Service / Network Service / WebDAV" in system settings. If your model supports Docker (some UGOS Pro models do), use the Linux → Docker option below.

### TrueNAS (CORE / SCALE)

1. Sharing → **Add WebDAV Share**, pick a dataset and set permissions
2. Enable the WebDAV service under Services
3. In Osmium: `http://<TrueNAS-IP>:<port>`

Official docs: truenas.com/docs, "WebDAV Share".

### Other Docker-capable NAS

Any Docker-capable NAS (Synology / QNAP / some UGREEN models / OMV / …) can use the generic Docker option in the Linux section below.

## Linux (server / PC)

### Option A: Docker (recommended, one command)

```bash
docker run --restart unless-stopped \
  -v /data/osmium-backups:/var/lib/dav/data \
  -e AUTH_TYPE=Basic -e USERNAME=osmium -e PASSWORD=your-password \
  -p 8080:80 -d bytemark/webdav
```

- Image: `bytemark/webdav` (Apache-based WebDAV, full PROPFIND support)
- In Osmium: `http://<server-IP>:8080`
- Backups land in the host directory `/data/osmium-backups`

### Option B: rclone (single binary, lightest)

```bash
# Install (official script)
curl https://rclone.org/install.sh | sudo bash
# Start the server (foreground; add a systemd unit for autostart)
rclone serve webdav /data/osmium-backups \
  --addr :8080 --user osmium --pass your-password
```

Official docs: rclone.org/commands/rclone_serve_webdav.

### Option C: Python wsgidav (pip)

```bash
pip install wsgidav cheroot
```

`wsgidav.yaml`:

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
        password: "your-password"
http_authenticator:
  accept_basic: true
```

```bash
wsgidav --config wsgidav.yaml
```

(This setup has been verified against Osmium.)

### Option D: Apache mod_dav

```bash
sudo apt install apache2
sudo a2enmod dav dav_fs
# Add a DAV directory in your site config, e.g.:
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

In Osmium: `http://<server-IP>/osmium` (include the path).

### ⚠️ Incompatible: nginx built-in dav module

nginx's `ngx_http_dav_module` only supports PUT/DELETE/MKCOL/COPY/MOVE — **no PROPFIND**. A server built with it can receive uploads, but Osmium's test-connection and restore features will fail. Use one of the options above instead.

## Android (phone)

> A phone as server is best for temporary use or testing: battery policies may kill the background process, and the IP changes between networks.

### Option A: Termux + rclone (verified)

```bash
pkg update && pkg install rclone
mkdir -p ~/backups
rclone serve webdav ~/backups --addr :8080 --user osmium --pass your-password
```

- Find the IP: System Settings → WLAN → current Wi-Fi details
- Keep it alive: install Termux:API and run `termux-wake-lock`; also allow Termux background running / battery non-optimization in system settings
- In Osmium: `http://<phone-IP>:8080`
- View backups inside Termux with `ls ~/backups/osmium/` (uninstalling Termux deletes them)

### Option B: MiXplorer

1. Open MiXplorer → menu (top-right) → **Servers** → start the **WebDAV server**
2. The screen shows an address like `http://192.168.1.x:8080`
3. In Osmium: use that address (leave the username blank)

## macOS

macOS has no built-in WebDAV server (Finder's "Connect to Server" is a client feature).

- **Option A**: `brew install rclone`, then `rclone serve webdav ~/backups --addr :8080 --user osmium --pass your-password`
- **Option B**: install Docker Desktop and use the Linux → Docker option
- In Osmium: `http://<Mac-IP>:8080`

## Windows

### Option A: IIS WebDAV (built-in)

1. Control Panel → Programs and Features → Turn Windows features on or off → expand **Internet Information Services** → World Wide Web Services → Common HTTP Features → tick **WebDAV Publishing**
2. Open **IIS Manager** → website (or create one) → right-click → **Add WebDAV Authoring Rules** → allow a user / all users read-write
3. Enable the **WebDAV Authoring** feature
4. Allow port 80 through Windows Firewall
5. In Osmium: `http://<PC-IP>` (append the path if published under a subdirectory)

### Option B: Docker Desktop

Install Docker Desktop, then use the Linux → Docker option (`-p 8080:80`).

### Option C: rclone

```powershell
winget install Rclone.Rclone
rclone serve webdav C:\osmium-backups --addr :8080 --user osmium --pass your-password
```

In Osmium: `http://<PC-IP>:8080`.

## iOS / iPadOS

iOS has no built-in WebDAV server. A few third-party WebDAV server apps exist on the App Store, but iOS suspends apps in the background, which kills the server — **not suitable as a backup target**. Use a NAS or a computer instead. (iOS devices also cannot run Osmium itself — it is an Android app.)

## HarmonyOS 5.0+ (NEXT)

- Osmium is an Android app; HarmonyOS NEXT does not run Android apps natively. To run Osmium on a HarmonyOS device, try Huawei's official Android compatibility tool (e.g. 卓易通 / OutFold) — compatibility is unverified
- WebDAV server options: **unknown — try it yourself**. Search the app gallery for WebDAV server apps, or use the Docker option above on Docker-capable devices

## Using Osmium (all platforms)

Note: opening the WebDAV backup screen requires verification (fingerprint / system credential / Osmium PIN).

1. Settings → Data → **WebDAV backup**
2. **Server address**: `http://IP:port`; hostnames work too (e.g. `http://nas.local:5005`). If the server is published under a subpath (e.g. `/webdav`), include the full path
3. **Username / Password**: server account; leave blank for anonymous servers
4. Tap "**Test connection**" to verify, then "**Save**" to remember the config (the password is stored encrypted on the device)
5. "**Back up now**" → set a backup password (8+ characters) → upload. This password is unrelated to the server password — **if you forget it, no backup can ever be restored**
6. "**Restore…**" → enter the backup password → pick a backup from the list → tick accounts → confirm import
7. "**Manage backups**" lists the backups on the server; tap the trash icon to delete one (confirmed, cannot be undone)

**Tip**: reserve a static lease (fixed IP) for the server in your router, or use its hostname, to avoid backups failing after IP changes.
