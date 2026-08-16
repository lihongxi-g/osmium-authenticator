# Osmium

Osmium is a privacy-first TOTP authenticator for Android. Every secret is encrypted with a hardware-backed Android Keystore key before it touches disk. The app creates no account and sends no telemetry — codes are computed entirely on device. The INTERNET permission exists solely for the optional WebDAV backup feature: the app connects exclusively to the server address you configure yourself (typically a NAS on your local network) and to nothing else.

## Features

- **TOTP and HOTP** — SHA-1 / SHA-256 / SHA-512, 6 or 8 digits, periods from 1 to 600 seconds
- **Google Authenticator migration** — scan the "Transfer accounts" QR code from Google Authenticator and import all accounts in one step
- **Encrypted backup** — export to a password-protected, PIN-bound encrypted file; restore on any device
- **WebDAV backup** — upload the same encrypted export to a WebDAV server on your local network (NAS, PC, another phone) and restore from it; the server only ever stores ciphertext
- **Steam Guard** — manual entry with the 26-character Steam alphabet (see warning below)
- **Hidden codes mode** — codes render as dots; copying still works, editing and sharing are locked until the mode is turned off
- **Sort modes** — random, alphabetical, add date, copy count
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

## Security notes

- Field-level AES-256-GCM encryption with a non-exportable Android Keystore key
- INTERNET permission used only for the user-configured WebDAV backup server; the app never connects to anything else — no analytics, no crash reporters, no cloud
- Backups are encrypted (PBKDF2 + AES-256-GCM) before they leave the device and are bound to the app PIN
- HTTPS uses standard certificate validation — self-signed certificates are rejected by design (no TrustAllManager in a password app)
- The self-destruct PIN wipes all data irreversibly from any PIN prompt

## Building

```bash
./gradlew assembleRelease
```

Release builds are minified with R8 and split by ABI. Unit tests: `./gradlew testDebugUnitTest`.

## License

MIT
