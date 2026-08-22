# Osmium

Osmium is a privacy-first TOTP authenticator for Android. This project is free software licensed under the GNU GPL v3 or later. Every secret is encrypted with a non-exportable Android Keystore key before it touches disk. The app creates no account and sends no telemetry — codes are computed entirely on device. The INTERNET permission exists solely for the optional WebDAV backup feature: the app connects exclusively to the server address you configure yourself (typically a NAS on your local network) and to nothing else.

## Features

- **TOTP and HOTP** — SHA-1 / SHA-256 / SHA-512, 6 or 8 digits, periods from 1 to 600 seconds
- **Google Authenticator migration** — scan the "Transfer accounts" QR code from Google Authenticator and import all accounts in one step
- **Encrypted backup** — export to a password-protected, PIN-bound encrypted file; restore on any device
- **WebDAV backup** — upload the same encrypted export to a WebDAV server on your local network (NAS, PC, another phone) and restore from it; the server only ever stores ciphertext
- **Automatic backup** — scheduled unattended backups to WebDAV or the phone's Download/Osmium folder; pick an interval in days, a time of day and how many backups to keep (1–10, default 5); next run shown with a GMT+8 label
- **Background-activity grant** — the Auto backup screen shows the live background-activity status and offers a one-tap request to exempt Osmium from battery optimizations
- **Update checks** — silent check for new releases on GitHub every time the app opens (opt-out in Settings); a dialog offers to open the GitHub releases page, the app never downloads or installs anything itself
- **Steam Guard** — manual entry with the 26-character Steam alphabet (see warning below)
- **Hidden codes mode** — codes render as dots; copying still works, editing and sharing are locked until the mode is turned off
- **Sort modes** — random, alphabetical, add date, copy count
- **Search** — find any account instantly by name or issuer
- **Tags** — create local multi-tags, filter accounts by tag, and keep tag metadata inside encrypted backups
- **Clock calibration** — manual offset for devices whose clock drifts
- **Security gate** — optional verification on open (fingerprint / system password / app PIN), self-destruct PIN, screenshots blocked by default
- **Nine languages** — English, 简体中文, Español, 日本語, 한국어, Deutsch, Русский, Français, हिन्दी
- **In-app manual** — feature guide and important notes
- **Terms & privacy in-app** — Settings → About shows the Terms of Use and Privacy Policy
- **Optional account name and issuer** — blank names are auto-generated from the add date and order (e.g. `20260801`); a blank issuer shows as `Unknown`

## ⚠ Steam Guard — read this first

**When adding a Steam account manually, you must enter `Steam` in the issuer (服务商) field.** Otherwise the account is treated as a regular TOTP entry and the generated codes will be wrong. Steam Guard codes are 5-character alphanumeric strings, not 6-digit numbers.

## Verify your authenticator

A live test page is available at **https://otp.osmium.im** — it issues a test secret, you add it to any authenticator, and enter the code back to verify. Supports TOTP, HOTP and Steam Guard. Everything runs in the browser; nothing is uploaded.

## Download

Pick the APK matching your device:

| File | Architecture | Devices |
|---|---|---|
| `app-arm64-v8a-release.apk` | arm64-v8a | Virtually all modern phones (recommended) |
| `app-armeabi-v7a-release.apk` | armeabi-v7a | Older 32-bit phones |
| `app-x86_64-release.apk` | x86_64 | Emulators |

Installing the wrong architecture will crash on launch. The signing certificate fingerprint (SHA-256) is `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.

## Privacy & Terms

- [Privacy Policy](PRIVACY.md) ([中文](PRIVACY-zh.md))
- [Terms of Use](TERMS.md) ([中文](TERMS-zh.md))

Both are also available in the app: Settings → About.

## WebDAV backup guide

Step-by-step server setup for every platform (NAS, Linux, Android, macOS, Windows, iOS, HarmonyOS): [WEBDAV-GUIDE.md](WEBDAV-GUIDE.md) ([中文](WEBDAV-GUIDE-zh.md))

## Battery & background behavior

Automatic backups are scheduled through the Android system scheduler — the app wakes briefly to run the backup and keeps **no persistent background service**. Battery drain beyond that short job is negligible.

Aggressive battery policies on some devices (ColorOS, MIUI, HyperOS, EMUI, One UI,OxygenOS,RealmeUI,PixelUI …) may defer or block scheduled backups. **To guarantee unattended night-time backups, we strongly recommend that you manually enable both of the following for Osmium in your system settings:**

1. **Allow auto-start** (auto-launch) — without it, the system freezes Osmium overnight and scheduled backups will not run.
2. **Allow full background activity** / turn OFF battery restrictions for Osmium — this lets the system scheduler wake the app for the short backup job.

The names and paths of these two switches **differ between manufacturers and Android versions** — please look for them in your own device's settings. For example, on ColorOS: Settings → Battery → More settings → allow full background activity for Osmium, and Phone Manager → App management → Auto-start management. The in-app Auto backup screen shows the current background-activity status and can open the system's battery-optimization dialog for you; auto-start, however, has no standard Android API and can only be enabled by hand. Android power saving may still run a backup a few minutes late — that is normal and expected.

## Security notes

- Field-level AES-256-GCM encryption with a non-exportable Android Keystore key
- INTERNET permission used only for the user-configured WebDAV backup server and the GitHub update check (opt-out) — no analytics, no crash reporters, no cloud
- WebDAV addresses starting with `http://` trigger an explicit plaintext-connection warning before they are saved
- Backups are encrypted (PBKDF2 + AES-256-GCM) before they leave the device and are bound to the app PIN
- HTTPS uses standard certificate validation — self-signed certificates are rejected by design (no TrustAllManager in a password app)
- The self-destruct PIN wipes all data irreversibly from any PIN prompt

## Building

```bash
./gradlew assembleRelease
```

Release builds are minified with R8 and split by ABI. Unit tests: `./gradlew testDebugUnitTest`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) or [COPYING](COPYING).