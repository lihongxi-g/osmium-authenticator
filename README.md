# Osmium

Osmium is a privacy-first Android TOTP/HOTP authenticator, released under the GNU GPL v3 or later. Codes are computed on-device; account data is encrypted before it is written using a non-exportable Android Keystore key. No account is required and no telemetry is collected.

The app itself fetches the Terms of Use and Privacy Policy from osmium.im when you open them (Settings → About), so you always see the current versions (no user data is sent). All other network features are optional: user-configured WebDAV backup, GitHub update checks (a switch each for stable releases and for preview builds, both off by default), and user-initiated encrypted transfer between two devices on the same Wi-Fi network. External links such as GitHub and the verification lab are opened by the system browser or another app.

## Features

- **TOTP and HOTP** — SHA-1 / SHA-256 / SHA-512, 6 or 8 digits, periods from 1 to 600 seconds
- **Google Authenticator migration** — scan the “Transfer accounts” QR code and import supported accounts in a batch
- **Import from another authenticator** — migrate in Settings → Data: Google Authenticator transfer QR codes, or plaintext export files of Aegis, 2FAS, Raivo OTP, andOTP, FreeOTP/FreeOTP+, Stratum/Authenticator Pro, Proton Authenticator and Bitwarden (JSON or CSV), plus any otpauth link list (Ente Auth, Google Auth URI export) and any CSV carrying otpauth links (KeePassXC, 1Password, Dashlane); the format is detected automatically and every entry is previewed with checkboxes before merging; unsupported entries are listed with a reason instead of being silently dropped
- **Export to another authenticator** — the migration exit door, in two shapes: **straight into Google Authenticator as QR codes** (`otpauth-migration`, ten accounts per code, paged when there are more, with the entries that format cannot carry listed up front), or to a file — otpauth link list (.txt), Aegis plaintext vault (.json) or KeePassXC CSV (.csv). Files are plaintext by nature, so the screen states that, and nothing is produced before the identity check
- **Appearance in one place** — Settings → Appearance now gathers theme, font, text size, vibration, sorting, tags and language; each row opens its own page, so the main settings list stays short
- **Interface fonts** — the system font, **Lora** or **Literata**, each previewed in its own typeface; plus a **weight slider** whose every stop is a real bundled font file (Lora 400–700, Literata 400–900), so a heavier setting always renders heavier text. Codes deliberately stay monospace. None of the bundled faces contains Chinese glyphs: a Chinese interface always uses the system font, and the font page explains that instead of offering settings that would do nothing (with a shortcut to the language page)
- **Text size** — a 80–200% slider behind a master switch, applied on top of the system font scale, with a live preview and a ceiling that keeps the PIN keypad and account cards intact
- **Vibration feedback** — a master switch and a 0–100% intensity slider. Every click gets a tick (buttons, switches, settings rows, the PIN keypad); scrolling, dragging and the swipe-back gesture stay silent. The app drives the vibrator itself, so it works even when the system touch-feedback switch is off
- **Encrypted backup** — export a password-protected encrypted file and restore it on a device running a compatible Osmium version
- **WebDAV backup** — upload encrypted backups to a WebDAV server you configure (NAS, PC, or another phone) and restore them; the server receives ciphertext only
- **LAN quick transfer** — connect two devices to the same Wi-Fi and transfer accounts through end-to-end encryption derived from a 6-digit pairing code; no cloud server is involved; the receiver can preview and select accounts
- **Automatic backup** — schedule backups to WebDAV or the phone’s Download/Osmium folder, with configurable interval, time and retention
- **Backup result notifications** — scheduled runs report their outcome: success is silent (no sound, no banner, no badge), failure or a skipped run alerts; the lock screen shows only that a background task finished, with time and destination revealed after unlocking; can be switched off at any time
- **Permissions page** — one place under Settings listing live status for camera, notifications, biometrics, local network, storage (Android 9 and older) and the battery-optimisation exemption, with the request one tap away
- **Verification when opening from a notification** — opening the auto-backup page from a backup notification asks for the fingerprint, screen lock or app PIN (on by default, can be switched off); that page can change the backup password
- **Update checks** — optionally query the GitHub Releases API for public version information, on two independent channels (stable releases, or stable plus preview builds; both switches off by default); no account or device data is sent, and the app never downloads or installs updates automatically
- **Steam Guard** — manually add Steam accounts and generate 5-character alphanumeric codes
- **Hidden codes mode** — render codes as dots; copying still works, while editing and sharing remain locked
- **Sort and search** — random, alphabetical, date-added or copy-count order; search by account name or issuer
- **Optional tags** — enable under Settings → Appearance → Tags; create multiple tags, filter accounts, and choose palette or custom `#RRGGBB` colors. When no tags exist, `Uncategorized` is not shown on the home screen
- **Security gate** — optional verification on open (biometrics / device credential / app PIN), self-destruct PIN, and screenshots blocked by default
- **Device-integrity detection (on-device, offline)** — a three-layer evidence chain plus hardware-backed Key Attestation (an embedded, pinned copy of Google's verifier) rates the device L0–L3 and lists every check in the in-app integrity report; no network, no telemetry. A root-manager app alone is treated as a hint, never as proof. When root is detected, the security features are forced on and five sensitive features (LAN transfer, WebDAV backup, auto backup, self-destruct setup, third-party import) are disabled until you lift the restriction in developer mode; a reminder appears when the app opens. Only matched signal names are logged — never secrets
- **Developer mode (hidden; English / Simplified Chinese)** — unlock by tapping the About title seven times and passing verification. Adds the integrity report with a detailed inspection log, Keystore encryption status, lifting root restrictions, plaintext export/import, forced re-encryption of the local database, hiding Settings entries, and 4/5/7-digit codes; dangerous actions require verification, a disclaimer and a typed confirmation phrase
- **Clock calibration** — manually compensate for device-clock drift
- **10 languages** — English, 简体中文, 繁體中文, Español, 日本語, 한국어, Deutsch, Русский, Français, हिन्दी
- **Built-in manual** — available offline; the **Terms of Use and Privacy Policy** are fetched from osmium.im when you open them in About (a website link is shown when they can't be fetched)

## ⚠ Steam Guard — read this first

When adding a Steam account manually, enter `Steam` in the issuer field. Otherwise the account is treated as regular TOTP and its codes will be wrong. Steam Guard codes are 5-character alphanumeric strings, not 6-digit numbers.

## Verify your authenticator

The live test page at **https://otp.osmium.im** supports TOTP, HOTP and Steam Guard. The test runs in the browser and uploads nothing.

## Download

Latest stable release: **Osmium v2.6.2** (`versionCode 821-823, per ABI`: 821 = armeabi-v7a, 822 = arm64-v8a, 823 = x86_64). Choose the ABI matching your device:

| File | Architecture | Devices | Download |
|---|---|---|---|
| `osmium-2.6.2-arm64-v8a.apk` | arm64-v8a | Virtually all modern phones (recommended) | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-arm64-v8a.apk) |
| `osmium-2.6.2-armeabi-v7a.apk` | armeabi-v7a | Older 32-bit phones | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-armeabi-v7a.apk) |
| `osmium-2.6.2-x86_64.apk` | x86_64 | Android emulators | [GitHub](https://github.com/lihongxi-g/osmium-authenticator/releases/download/v2.6.2/osmium-2.6.2-x86_64.apk) |

Installing an incompatible ABI may prevent the app from starting. All three APKs use the same signing certificate, SHA-256 fingerprint: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.

APK SHA-256 checksums are listed in the [v2.6.2 release notes](release-notes-v2.6.2.md) and in `SHA256SUMS.txt` on the GitHub Release page.

### F-Droid

Osmium is on F-Droid: **https://f-droid.org/packages/com.safekey.authenticator/**

F-Droid builds from the same source and signs with the same certificate as the GitHub release (the recipe pins the signing key), so the two are the same app — moving between them is an in-place update, and for a 2FA app that matters because uninstalling deletes every local account. Note that the two channels number their versions differently: GitHub uses the versionCode below (821–823 for 2.6.2), F-Droid derives its own (791–793 for 2.6.1), and the GitHub number is the higher one. Switching from F-Droid to GitHub therefore upgrades; switching back to F-Droid waits until an F-Droid build catches up.

F-Droid's index refreshes on its own schedule, so a brand-new release can take a few days to appear there; the [package page](https://f-droid.org/packages/com.safekey.authenticator/) always shows the version actually installable right now. As of this release it offers **2.6.1**.

## Privacy & Terms

- [Privacy Policy](PRIVACY.md) ([中文](PRIVACY-zh.md)) — also on the website: [osmium.im/privacypolicy](https://osmium.im/privacypolicy/)
- [Terms of Use](TERMS.md) ([中文](TERMS-zh.md)) — also on the website: [osmium.im/useragreement](https://osmium.im/useragreement/)

The app shows the current version under Settings → About; it fetches each document from the website when you open it in About. LAN transfer and WebDAV backup are user-initiated; LAN transfer uses pairing-code-derived end-to-end encryption and does not pass through an Osmium server.

## WebDAV backup guide

Step-by-step setup for WebDAV servers: [WEBDAV-GUIDE.md](WEBDAV-GUIDE.md) ([中文](WEBDAV-GUIDE-zh.md)). WebDAV and LAN quick transfer are separate features: WebDAV is for a backup server, while LAN transfer is for another device on the same local network.

## Battery & background behavior

Scheduled backups use the Android system scheduler: the app wakes briefly for a backup and keeps no persistent background service. Device power policies may defer or block scheduled work, so an overdue backup is also run when you open the app, and "Back up now" runs immediately inside the app and reports its result. See the in-app Auto backup page for the permission guidance. Note that swiping the app away from the recents list counts as a force-stop on some systems and clears scheduled wake-ups until you open the app again — no application logic can override that, so avoid swiping it away or remove the battery restriction for Osmium. Backup result notifications can likewise be switched off by you or by the system.

## Building

```bash
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

Release builds are minified with R8 and split by ABI. Build environment: Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10, Jetpack Compose BOM 2024.09.03, `compileSdk 37`, `minSdk 26`, `targetSdk 37`. GitHub Actions runs unit tests and builds Release APKs. Official release assets always use the format `osmium-version-architecture.apk`.

## Embedded components

The device-integrity feature embeds a pinned, source-level copy of Google's [android/keyattestation](https://github.com/android/keyattestation) verifier (Apache-2.0; see [keyattestation/NOTICE](keyattestation/NOTICE)) with a small set of documented modifications. Its upstream test suite — real-device attestation chains in `keyattestation/testdata/` — runs in CI via `./gradlew :keyattestation:test`. The detection heuristics take inspiration from community research (for example the Duck Detector project) without reusing any code; both are credited in the app under Settings → About → Attributions. Build-time snapshots shipped inside the APK — `keyattestation/roots.json` and `app/src/main/assets/attestation_status.json` — are sourced from Google's public attestation endpoints (`android.googleapis.com/attestation/…`) and refreshed best-effort by `scripts/fetch_attestation_data.py`; when the refresh fails, the repository copy is used.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) or [COPYING](COPYING).

The optional interface fonts — **Lora** and **Literata** — are bundled unmodified under the SIL Open Font License 1.1 (only the file names were lowercased for Android resources); the full licence texts ship inside the APK and are listed in the app under Settings → About → Attributions. See [THIRD-PARTY-FONTS.md](THIRD-PARTY-FONTS.md).
