# Osmium

Osmium is a privacy-first Android TOTP/HOTP authenticator, released under the GNU GPL v3 or later. Codes are computed on-device; account data is encrypted before it is written using a non-exportable Android Keystore key. No account is required and no telemetry is collected.

The app itself fetches the latest Terms of Use and Privacy Policy from osmium.im each time it opens, so you always see the current versions (no user data is sent). All other network features are optional: user-configured WebDAV backup, GitHub update checks, and user-initiated encrypted transfer between two devices on the same Wi-Fi network. External links such as GitHub and the verification lab are opened by the system browser or another app.

## Features

- **TOTP and HOTP** — SHA-1 / SHA-256 / SHA-512, 6 or 8 digits, periods from 1 to 600 seconds
- **Google Authenticator migration** — scan the “Transfer accounts” QR code and import supported accounts in a batch
- **Import from another authenticator** — migrate in Settings → Data: Google Authenticator transfer QR codes, or plaintext export files of Aegis, 2FAS and Raivo OTP; the format is detected automatically and every entry is previewed with checkboxes before merging; unsupported entries are listed with a reason instead of being silently dropped
- **Encrypted backup** — export a password-protected encrypted file and restore it on a device running a compatible Osmium version
- **WebDAV backup** — upload encrypted backups to a WebDAV server you configure (NAS, PC, or another phone) and restore them; the server receives ciphertext only
- **LAN quick transfer** — connect two devices to the same Wi-Fi and transfer accounts through end-to-end encryption derived from a 6-digit pairing code; no cloud server is involved; the receiver can preview and select accounts
- **Automatic backup** — schedule backups to WebDAV or the phone’s Download/Osmium folder, with configurable interval, time and retention
- **Update checks** — optionally query the GitHub Releases API for public version information; no account or device data is sent, and the app never downloads or installs updates automatically
- **Steam Guard** — manually add Steam accounts and generate 5-character alphanumeric codes
- **Hidden codes mode** — render codes as dots; copying still works, while editing and sharing remain locked
- **Sort and search** — random, alphabetical, date-added or copy-count order; search by account name or issuer
- **Optional tags** — enable under Settings → Appearance → Tags; create multiple tags, filter accounts, and choose palette or custom `#RRGGBB` colors. When no tags exist, `Uncategorized` is not shown on the home screen
- **Security gate** — optional verification on open (biometrics / device credential / app PIN), self-destruct PIN, and screenshots blocked by default
- **Device-integrity detection (on-device, offline)** — a three-layer evidence chain plus hardware-backed Key Attestation (an embedded, pinned copy of Google's verifier) rates the device L0–L3 and lists every check in the in-app integrity report; no network, no telemetry. A root-manager app alone is treated as a hint, never as proof. When root is detected, the security features are forced on and five sensitive features (LAN transfer, WebDAV backup, auto backup, self-destruct setup, third-party import) are disabled until you lift the restriction in developer mode; a reminder appears when the app opens. Only matched signal names are logged — never secrets
- **Developer mode (hidden; English / Simplified Chinese)** — unlock by tapping the About title seven times and passing verification. Adds the integrity report with a detailed inspection log, Keystore encryption status, lifting root restrictions, plaintext export/import, forced re-encryption of the local database, hiding Settings entries, and 4/5/7-digit codes; dangerous actions require verification, a disclaimer and a typed confirmation phrase
- **Clock calibration** — manually compensate for device-clock drift
- **10 languages** — English, 简体中文, 繁體中文, Español, 日本語, 한국어, Deutsch, Русский, Français, हिन्दी
- **Built-in manual** — available offline; the **Terms of Use and Privacy Policy** are fetched from osmium.im each time the app opens (a website link is shown when they can't be fetched)

## ⚠ Steam Guard — read this first

When adding a Steam account manually, enter `Steam` in the issuer field. Otherwise the account is treated as regular TOTP and its codes will be wrong. Steam Guard codes are 5-character alphanumeric strings, not 6-digit numbers.

## Verify your authenticator

The live test page at **https://otp.osmium.im** supports TOTP, HOTP and Steam Guard. The test runs in the browser and uploads nothing.

## Download

Latest stable release: **Osmium v2.5.2** (`versionCode 621-623, per ABI`). Choose the ABI matching your device:

| File | Architecture | Devices | Download |
|---|---|---|---|
| `osmium-2.5.2-arm64-v8a.apk` | arm64-v8a | Virtually all modern phones (recommended) | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.5.2/osmium-2.5.2-arm64-v8a.apk) |
| `osmium-2.5.2-armeabi-v7a.apk` | armeabi-v7a | Older 32-bit phones | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.5.2/osmium-2.5.2-armeabi-v7a.apk) |
| `osmium-2.5.2-x86_64.apk` | x86_64 | Android emulators | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.5.2/osmium-2.5.2-x86_64.apk) |

Installing an incompatible ABI may prevent the app from starting. All three APKs use the same signing certificate, SHA-256 fingerprint: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.

APK SHA-256 checksums are listed in the [v2.5.2 release notes](release-notes-v2.5.2.md) and on the GitHub Release page.

## Privacy & Terms

- [Privacy Policy](PRIVACY.md) ([中文](PRIVACY-zh.md)) — also on the website: [osmium.im/privacypolicy](https://osmium.im/privacypolicy/)
- [Terms of Use](TERMS.md) ([中文](TERMS-zh.md)) — also on the website: [osmium.im/useragreement](https://osmium.im/useragreement/)

The app shows the current version under Settings → About; it fetches both documents from the website each time it opens. LAN transfer and WebDAV backup are user-initiated; LAN transfer uses pairing-code-derived end-to-end encryption and does not pass through an Osmium server.

## WebDAV backup guide

Step-by-step setup for WebDAV servers: [WEBDAV-GUIDE.md](WEBDAV-GUIDE.md) ([中文](WEBDAV-GUIDE-zh.md)). WebDAV and LAN quick transfer are separate features: WebDAV is for a backup server, while LAN transfer is for another device on the same local network.

## Battery & background behavior

Automatic backups run through the Android system scheduler. The app wakes briefly for a backup and keeps no persistent background service. Device power policies may defer or block scheduled work; follow the guidance in the README and the in-app Auto backup page.

## Building

```bash
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

Release builds are minified with R8 and split by ABI. Build environment: Kotlin 1.9, Jetpack Compose BOM 2024.09.03, `compileSdk 35`, `minSdk 26`, `targetSdk 34`. GitHub Actions runs unit tests and builds Release APKs. Official release assets always use the format `osmium-version-architecture.apk`.

## Embedded components

The device-integrity feature embeds a pinned, source-level copy of Google's [android/keyattestation](https://github.com/android/keyattestation) verifier (Apache-2.0; see [keyattestation/NOTICE](keyattestation/NOTICE)) with a small set of documented modifications. Its upstream test suite — real-device attestation chains in `keyattestation/testdata/` — runs in CI via `./gradlew :keyattestation:test`. The detection heuristics take inspiration from community research (for example the Duck Detector project) without reusing any code; both are credited in the app under Settings → About → Attributions. Build-time snapshots shipped inside the APK — `keyattestation/roots.json` and `app/src/main/assets/attestation_status.json` — are sourced from Google's public attestation endpoints (`android.googleapis.com/attestation/…`) and refreshed best-effort by `scripts/fetch_attestation_data.py`; when the refresh fails, the repository copy is used.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) or [COPYING](COPYING).
