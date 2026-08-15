# Osmium Privacy Policy

*Effective date: August 15, 2026*

Osmium is designed to be fully offline. This policy describes, in plain terms, what the application does and does not do with your data.

## Data we collect

**We do not collect any data.** Specifically:

- The application holds **no INTERNET permission** in its manifest. It cannot transmit anything over a network, even if it wanted to.
- There is **no account system, no registration, no analytics, no advertising SDK, and no crash reporter**.
- We operate **no server** for the application. There is nothing to upload to and nothing to sync with.

## Data stored on your device

- Your authenticator secrets, account names and issuers are stored **only on your device**, encrypted with AES-256-GCM using a non-exportable key in the Android Keystore.
- Backup files you export are encrypted (PBKDF2 + AES-256-GCM) and are stored wherever you choose to save them. We have no access to them.
- The optional app PIN and self-destruct PIN are stored as salted hashes and encrypted values; we cannot recover them and neither can anyone else.

## Permissions

- **Camera** — used solely to scan QR codes during account import. Frames are processed in real time on the device (ML Kit, bundled offline mode) and are never stored or transmitted.
- **Biometrics / device credential** — handled exclusively by the Android system. The application never sees or stores fingerprint or face data.

## Data sharing

We share nothing, because we hold nothing. No third party receives data from the application.

## Data deletion

- Uninstalling the application deletes all locally stored data.
- The self-destruct PIN, when configured and entered, irreversibly destroys all accounts and settings.
- Backups are deleted when you delete the exported files.

## Changes to this policy

The application is open source (MIT). Any future change that would alter these guarantees will be stated in the release notes and in this document.

## Contact

Email: zhif0776@hotmail.com · Telegram: https://t.me/osmium2fa
