# Osmium Privacy Policy

*Effective date: September 4, 2026*

Osmium is a privacy-first Android TOTP/HOTP authenticator. This policy explains, in plain terms, what the application does and does not do with your data.

## Data we collect

**We do not collect, sell or use personal data for advertising analytics.** Specifically:

- There is no account system, registration, analytics, advertising SDK or crash reporter.
- We operate no Osmium cloud account or synchronization server.
- Network requests occur only when the relevant feature is enabled or initiated: user-configured WebDAV backup, GitHub update checks, and a LAN quick transfer that you start yourself.

## Storage on your device

- Your authenticator secrets, account names, issuers, tags and tag colors are stored on your device and encrypted with AES-256-GCM using a non-exportable key in the Android Keystore.
- Exported backup files are encrypted with your password (PBKDF2 + AES-256-GCM) and saved wherever you choose; the developer cannot access them.
- The optional app PIN and self-destruct PIN are stored as salted hashes or protected ciphertext and cannot be recovered by the developer.
- WebDAV server addresses and login information are stored locally; the password is protected by the Android Keystore.
- The tag feature switch and tag-filter selection are application/UI state. Turning tags off does not upload or delete the tags already stored locally.

## Network: WebDAV backup

When WebDAV backup is enabled, the app connects only to the server address you enter, and only when you test the connection, upload a backup, list backups or restore one. Backups are encrypted with your export password before leaving the device; the server stores ciphertext only. A plaintext `http://` address requires explicit on-screen confirmation before it is saved. The address may identify a local device or a remote server that you manage; assess its trustworthiness yourself.

## Network: GitHub update checks

When automatic update checks are enabled (on by default), the app asks the GitHub Releases API (`api.github.com`) for public version information. The request contains no account data or device identifier. A failed check is silent, and the app never downloads or installs an update itself. After saved settings have loaded, turning the switch off prevents the app from initiating normal update checks; during cold start there may be a short window before preferences finish loading in which one check can be triggered.

## Network: LAN quick transfer

LAN quick transfer is started by you between two devices. Both devices must be connected to the same Wi‑Fi network. The app discovers a peer or uses an IP/port that you enter, establishes a temporary connection, and transfers account data with AES-256-GCM encryption derived from a 6-digit pairing code. Account data does not pass through an Osmium cloud service or another third-party server. The receiver can preview and select accounts before importing them. Osmium cannot control LAN isolation, router behavior or other devices on the network; share the pairing code only with the intended receiver.

## Automatic backup and background execution

If automatic backup is enabled, Android’s system scheduler runs it on a schedule. You can choose WebDAV or the phone’s Download/Osmium folder. The backup password is stored locally under Android Keystore protection and is used only to encrypt backup files. Local backups are pruned according to your retention setting. The app keeps no persistent background service, but manufacturer power policies may defer or block scheduled work.

The Auto backup screen can ask Android to show the battery-optimization exemption dialog. Android decides whether to grant it; the app reads no additional personal data from that dialog.

## Permissions

- **Camera** — used only to scan QR codes during account import; frames are processed on-device and never stored or transmitted.
- **Biometrics / device credentials** — handled by Android; the app does not access fingerprint or face data.
- **Internet** — used for the WebDAV, GitHub update-check and LAN-transfer functions described above.
- **Wi-Fi state and multicast** — used to discover and connect to LAN transfer peers; discovery and the transfer service stop when the LAN transfer screen is left.
- **Storage (Android 8/9 only)** — `WRITE_EXTERNAL_STORAGE` is used only to write automatic backups to the public Download/Osmium folder; Android 10 and later do not need it.
- **ACCESS_LOCAL_NETWORK** — pre-declared for future Android/target-SDK local-network access requirements; the current `targetSdk 34` does not actively request this runtime permission.

## Data sharing

We do not share data with advertisers, analytics services or an Osmium cloud because we do not hold that data. A WebDAV server receives encrypted backup files you actively upload. The intended receiving device receives account data from a transfer you actively start. GitHub receives only a public version-information request, not account or device data.

## Data deletion

- Uninstalling the app or clearing its data deletes local application data, but does not automatically delete exported files, backups in the public Download folder, WebDAV backups or data already received by another device.
- Configuring and entering the self-destruct PIN irreversibly destroys local accounts, PINs, settings and the local encryption master key. It does not delete backups or received copies in other locations.
- You can delete exported files and WebDAV/LAN-received copies yourself; automatic backups also remove older files according to the retention setting.

## Changes to this policy

The application is open source under GPL-3.0-or-later. Any future change to the data practices described here will be stated in the release notes and in this document.

## Contact

Email: zhif0776@hotmail.com · Telegram: https://t.me/osmium2fa
