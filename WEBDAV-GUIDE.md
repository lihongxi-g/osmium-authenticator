# Osmium / WebDAV Backup Guide

These documents describe WebDAV backup and restore in Osmium v2.3.9. WebDAV backup and LAN quick transfer are separate features: WebDAV connects to a backup server you configure; LAN transfer connects to another device on the same Wi‑Fi.

## What the Osmium WebDAV client needs

Osmium uses these WebDAV methods:

- `PROPFIND`: test the connection and list backups
- `MKCOL`: create the backup directory
- `PUT`: upload an encrypted backup
- `GET`: download a backup
- `DELETE`: delete old backups from Manage backups

The server must support HTTP Basic authentication; anonymous servers are also supported. Backups are encrypted on the device first with PBKDF2 + AES-256-GCM, so the server receives ciphertext only.

For an `http://` address, Osmium asks for explicit confirmation before saving it; HTTPS is still recommended for real deployments. Self-signed certificates not trusted by the device are rejected.

## NAS

### Synology DSM

1. Install **WebDAV Server** from Package Center.
2. Enable HTTP (usually 5005) or HTTPS (usually 5006).
3. Grant read/write access to a dedicated shared folder.
4. In Osmium, enter `http://<NAS-IP>:5005` or the corresponding HTTPS address and provide credentials.

### QNAP

Control Panel → Applications → Web Server → WebDAV: enable it and configure shared-folder permissions. Enter `http://<NAS-IP>:<port>` in Osmium.

### ZSpace

System Settings → File & Sharing Services → WebDAV Service → enable it. A least-privilege dedicated account is recommended.

### UGREEN UGOS Pro

Control Panel → File Service → WebDAV → enable service → Apply, then configure the port and permissions.

### TrueNAS

- SCALE: Shares → WebDAV widget → Add.
- CORE: Sharing → WebDAV Shares → Add and choose a dataset.

## Linux / macOS / Windows

### Docker (Linux or Docker Desktop)

```bash
docker run --restart unless-stopped \
  -v /data/osmium-backups:/var/lib/dav/data \
  -e AUTH_TYPE=Basic -e USERNAME=osmium -e PASSWORD=your-password \
  -p 8080:80 -d bytemark/webdav
```

Osmium address: `http://<server-IP>:8080`.

### rclone

```bash
curl https://rclone.org/install.sh | sudo bash
rclone serve webdav /data/osmium-backups \
  --addr :8080 --user osmium --pass your-password
```

### Apache mod_dav

```bash
sudo apt install apache2
sudo a2enmod dav dav_fs
sudo systemctl restart apache2
```

Enable `Dav On`, Basic authentication and read/write permissions for the target directory in the Apache site.

### Windows IIS

Install WebDAV Publishing and Basic Authentication through Server Manager. Create a dedicated directory and authorization rules. Open only the port you actually use in Windows Firewall.

### macOS

macOS has no native WebDAV server suitable for long-term use. Use Docker or rclone; Finder’s “Connect to Server” is a client, not a WebDAV server.

### Not recommended: nginx’s built-in DAV module

nginx’s `ngx_http_dav_module` generally does not support `PROPFIND`. Uploads may appear to work, while Osmium’s connection test, backup listing and restore fail. Use a complete WebDAV server instead.

## Temporary Android server

Using a phone as a server is recommended only temporarily or for testing. Battery policies can kill the background process, and the IP can change when Wi‑Fi changes.

### Termux + rclone

```bash
pkg update && pkg install rclone
mkdir -p ~/backups
rclone serve webdav ~/backups --addr :8080 --user osmium --pass your-password
```

Allow Termux to run in the background and disable its battery optimization. Enter the phone’s LAN IP in Osmium.

### MiXplorer

Open MiXplorer → menu → Servers → start the WebDAV server, then use the displayed address.

## Using Osmium

1. Settings → Data → **WebDAV backup**.
2. Enter the server address, username and password.
3. Tap **Test connection** and confirm that `PROPFIND` succeeds.
4. Tap **Save**.
5. Enter the separate backup password when backing up or restoring; it is not the WebDAV login password.
6. Manage backups can list and delete old files on the server; confirm the target before deleting.

## LAN quick transfer is not WebDAV

For a phone-to-phone move, use **LAN quick transfer** in Settings instead:

1. Connect both devices to the same Wi‑Fi.
2. Open the transfer page on the sender and display the pairing code.
3. On the receiver, discover the sender or enter its IP/port manually.
4. Enter the 6-digit pairing code, preview the accounts and select what to import.

LAN transfer uses temporary AES-256-GCM encryption derived from the pairing code and does not pass through an Osmium cloud service or the WebDAV server. Share the pairing code only with the intended receiver.
