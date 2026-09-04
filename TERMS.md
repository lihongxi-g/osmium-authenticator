# Osmium Terms of Use

*Effective date: September 4, 2026*

By installing or using Osmium, you agree to the following terms.

## License

Osmium is free and open source software released under the GNU General Public License, version 3 or later (GPL-3.0-or-later). You may use, copy, modify and redistribute it in accordance with that license, which is included in this repository (LICENSE/COPYING).

## Provided “as is”

The software is provided without warranty of any kind, express or implied. To the maximum extent permitted by law, the author is not liable for any damages arising from the use of or inability to use the software.

## Your responsibilities

- **Safeguard your secrets, PINs and backup passwords.** Osmium uses unrecoverable encryption keys; the developer and third parties cannot reset a forgotten PIN or recover a lost backup password. The automatic-backup password is stored on your device, encrypted with the Android Keystore, and is likewise lost if app data is erased.
- **Keep your own backups.** Uninstalling the app or clearing its data permanently deletes local accounts and settings. The developer cannot recover them. Automatic backups run through the Android system scheduler and depend on the device’s power state, background settings and manufacturer power policies. LAN quick transfer is a user-initiated temporary transfer, not cloud synchronization, and is not guaranteed to continue in the background.
- **Review received data.** LAN quick transfer requires both devices to be on the same Wi‑Fi network and uses a 6-digit pairing code for one transfer session. Review the import preview and select only the accounts you need. Do not share the pairing code with untrusted people.
- **The self-destruct PIN is irreversible.** Entering a configured self-destruct PIN in a supported PIN screen permanently destroys all local accounts, PINs, settings and the local encryption master key, by design.
- **Keep your device clock accurate.** Time-based codes depend on the device clock; the app offers calibration but cannot fix an incorrect system clock on its own.
- **Follow applicable law and network rules.** You choose the WebDAV addresses, LAN-transfer peers and backup locations. Use only devices, servers and accounts that you control or are authorized to access.

## Permitted use

You may use Osmium for your own two-factor authentication and to migrate accounts between devices you control or are authorized to use. You may not use it to access systems or data without authorization.

## Networking and third-party services

Osmium has no account system and provides no cloud synchronization. It may perform the following network activity:

- **The WebDAV server you configure yourself**: optional backup, backup listing and restore; backups are encrypted with your password before upload.
- **The GitHub Releases API (`api.github.com`)**: optional version checking; it requests public release information and sends no account or device data.
- **LAN quick transfer**: actively initiated by you between two devices, using AES‑256‑GCM encryption derived from a 6-digit pairing code. Account data does not pass through Osmium or another cloud server. Whether other devices on the local network can discover or interfere with a connection depends on the network environment; give the pairing code only to the intended receiver.

When you open GitHub, the verification lab or another external link, the system browser or another app handles that link. Its behavior is governed by the relevant third party’s terms and privacy policy.

The optional Google Authenticator migration feature parses the export code you provide locally on the device; it does not send data to Google or anyone else.

## Contact

Email: zhif0776@hotmail.com · Telegram: https://t.me/osmium2fa
