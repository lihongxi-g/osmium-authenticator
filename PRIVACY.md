# Osmium Privacy Policy

*Effective date: August 16, 2026*

Osmium is a privacy-first TOTP authenticator designed to run fully offline. This policy describes, in plain terms, what the application does and does not do with your data.

## Data we collect

**We do not collect any data.** Specifically:

- There is **no account system, no registration, no analytics, no advertising SDK, and no crash reporter**.
- We operate **no server** for the application. There is nothing to upload to and nothing to sync with.
- The INTERNET permission exists only for two optional features, both described below: WebDAV backup and update checks.

## Storage on your device

- Your authenticator secrets, account names and issuers are stored **only on your device**, encrypted with AES-256-GCM using a non-exportable key in the Android Keystore.
- Backup files you export are encrypted with your password (PBKDF2 + AES-256-GCM) and stored wherever you choose to save them. We have no access to them.
- The optional app PIN and self-destruct PIN are stored as salted hashes and encrypted values; they cannot be recovered by anyone, including us.
- The WebDAV server address and login are stored on your device, with the password encrypted by the Android Keystore.

## Network: WebDAV backup

If you enable WebDAV backup (Settings → Data → WebDAV backup), the app connects **exclusively** to the server address you enter — typically a NAS, a PC, or another device on your local network — and only when you run a backup, list backups, or restore one. Backups are encrypted with your export password **before they leave the device**; the server only ever stores ciphertext. Plaintext `http://` addresses require an explicit on-screen confirmation before they are saved.

## Network: update checks

If auto-check for updates is enabled (Settings → About → Auto-check for updates; on by default), the app asks the GitHub releases API (`api.github.com`) for the latest public version each time it opens (with a short internal cooldown). No account data or device information is sent. The check fails silently when offline and never downloads or installs anything.

## Automatic backup

If you enable automatic backup (Settings → Data → Auto backup), the app runs scheduled backups **unattended** via the Android system scheduler: choose WebDAV or the phone's Download/Osmium folder, an interval in days, a time of day, and how many backups to keep (1–10, default 5; older ones are pruned automatically). The export password you set is stored on your device, encrypted with the Android Keystore key, and used only to encrypt backup files. Local backups are written to the public Download/Osmium folder.

## Background execution

Automatic backups run through the Android system scheduler. The app wakes briefly to run a backup and keeps **no persistent background service**; beyond that short job it does not consume battery in the background. On some devices, aggressive battery policies may defer or block scheduled backups — see the README for a recommendation.

The Auto backup screen displays the current background-activity status and can request the system's battery-optimization exemption for Osmium (the standard `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog). The request and its result are handled entirely by the Android system; the app reads no additional data from it.

## Permissions

- **Camera** — used solely to scan QR codes during account import. Frames are processed in real time on the device (ML Kit, bundled offline mode) and are never stored or transmitted.
- **Biometrics / device credential** — handled exclusively by the Android system. The application never sees or stores fingerprint or face data.
- **Internet** — used solely for the WebDAV backup feature and the update check described above.
- **Storage (Android 8/9 only)** — `WRITE_EXTERNAL_STORAGE` is requested only on Android 8 and 9, solely to write automatic backups to the public Download/Osmium folder. Android 10 and later need no such permission.

## Data sharing

We share nothing, because we hold nothing. No third party receives data from the application.

## Data deletion

- Uninstalling the application deletes all locally stored data.
- The self-destruct PIN, when configured and entered, irreversibly destroys all accounts and settings.
- Backups are deleted when you delete the exported files; automatic backups are additionally pruned by the retention setting.

## Changes to this policy

The application is open source (MIT). Any future change that would alter these guarantees will be stated in the release notes and in this document.

## Contact

Email: zhif0776@hotmail.com · Telegram: https://t.me/osmium2fa
