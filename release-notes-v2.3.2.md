# Osmium v2.3.2

Released 2026-08. New features, security hardening, and documentation updates.

## New

- **Automatic backup** — scheduled unattended backups. Pick a target (WebDAV server or the phone's Download/Osmium folder), an interval in days, and a time of day. The next run time is shown with an explicit GMT+8 label. The last run result (success or failure reason) is shown on the screen.
- **Update checks** — when enabled (on by default), the app silently asks the GitHub releases API for the latest version once per day on open. If a newer version exists, a dialog offers to open the GitHub releases page. The app never downloads or installs anything by itself. Opt-out in Settings → About.

## Security

- WebDAV server addresses starting with `http://` now trigger an explicit plaintext-connection warning before they are saved, instead of being accepted silently.
- The automatic-backup export password is stored encrypted with the Android Keystore key and is used only to encrypt backup files. Backups use the same password-encrypted export format (PBKDF2 + AES-256-GCM) as manual backups. Local auto-backups are pruned to the 5 newest.
- README wording aligned with the actual implementation: secrets are encrypted with a non-exportable Android Keystore key (the previous "hardware-backed" wording was stronger than what the code can guarantee on every device). A `LICENSE` file (MIT) was added to the repository — previously only the README mentioned MIT.
- Privacy policy and terms updated in-app and on GitHub to disclose the update check and the automatic-backup password storage. Nothing else changed: no accounts, no analytics, no crash reporters, no cloud.

## Fixed

- None (no bug fixes in this release).

## Docs

- README (EN/ZH) feature lists and security notes updated; the built-in manual (all 9 languages) gained "Automatic backup" and "Update checks" sections.

The signing certificate fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
