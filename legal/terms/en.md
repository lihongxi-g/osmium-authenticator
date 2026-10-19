# Terms of Use

*Effective date: October 16, 2026 · Applies to Osmium v2.5.6 and later*

*Current version, published at osmium.im. The app reads it when you open About → Terms of Use.*

By installing or using Osmium, you agree to the following terms.

Osmium is free and open source software released under the GNU General Public License version 3 or later (GPL-3.0-or-later; see LICENSE/COPYING).

The software is provided "as is", without warranty of any kind. To the maximum extent permitted by law, the developer is not liable for damages arising from its use.

Osmium generates TOTP and HOTP codes with 4, 5, 6, 7 or 8 digits and supports SHA-1, SHA-256 and SHA-512, 30- and 60-second periods, Steam Guard codes, tags and search. All of it runs on your device.

## Your responsibilities:

- Safeguard your secrets, PINs and backup passwords. Encryption keys cannot be recovered by the developer or third parties; a forgotten PIN, a lost backup password or cleared app data cannot be restored by anyone.
- Keep your own backups. Uninstalling the app or clearing its data permanently deletes local accounts and settings. Automatic backups depend on Android and manufacturer scheduling and may be deferred or blocked by power-saving policies.
- Import only files you trust. Osmium imports from Google Authenticator transfer QR codes and from export files of Aegis, 2FAS and Raivo OTP. Review every entry in the preview before importing, confirm the file really came from the authenticator you intend to leave, and delete export files after use - they contain your secrets in plaintext. A backup file whose entries use code parameters Osmium cannot reproduce faithfully (a code length or algorithm outside the supported set) is refused as a whole rather than imported in part.
- Review received data. LAN quick transfer requires both devices on the same Wi-Fi network: the receiving device shows a 12-digit pairing code and the sending device must enter it before anything is transferred. Each device then shows the network risks it detected - on itself and on the other device - and nothing is sent or accepted until both sides confirm; cancelling the dialog aborts the transfer. Share the code only with the device you are sending from, and confirm the entries in the import preview. Both devices must run a version that speaks the same transfer protocol.
- The self-destruct PIN is irreversible. Keep your device clock accurate and follow applicable law and network rules for the servers and devices you configure.
- Rooted devices. When Osmium detects that the device is rooted, it forces on its protections (verification on open, screenshot blocking, hidden codes) and disables LAN quick transfer, WebDAV backup, automatic backups, self-destruct configuration and third-party import. These restrictions can only be lifted in Developer mode. Root detection runs locally on the device and is best-effort.
- Device integrity report. At launch Osmium also checks device integrity locally: system, mount, kernel, boot-state and app-signature checks, cross-checks between them, and hardware-backed key attestation (TEE certificate chain, verified-boot chain and boot hash) verified on this device with Google's official verifier. The report is shown on your device only; when the result is suspicious or compromised, the app reminds you once per app open. A root-manager app being installed is treated as a hint, not as proof. No software check is perfectly accurate, so read the report as guidance about your device rather than a guarantee. Developer mode can hide this feature; once hidden, the app no longer runs these checks at launch and no longer shows the reminder.
- Developer mode. Hidden behind seven taps on the app name in About and protected by identity verification, it can lift the root restrictions, hide settings entries, export your vault as plaintext, and force re-encryption of the local database. It also offers an optional detailed detection log - integrity checks write no log unless you enable it. A plaintext export contains all of your secrets without encryption — anyone who obtains the file can read every account. You use developer mode at your own risk; the developer is not liable for losses caused by it or by plaintext exports.

You may use Osmium for your own two-factor authentication and for devices you control or are authorized to use.

Osmium has no account system and no cloud synchronization; all import features run entirely on this device. The app makes no network request at start-up unless you switch on the optional version check. Otherwise it connects only when you use a feature that needs it: the WebDAV server you configure, the GitHub Releases API for the optional version check (off by default; you can switch it on in Settings), the osmium.im website to read the current versions of these Terms of Use and of the Privacy Policy when you open them in About, Google's public attestation status list when you refresh the revocation data on the integrity screen, or the other device during a LAN transfer. LAN transfer uses AES-256-GCM encryption derived from the pairing code and does not pass through an Osmium cloud server.

The current versions of these Terms of Use and of the Privacy Policy are published on the osmium.im website, and the versions published there are the ones that apply. The app reads them when you open the document from About and keeps the copy it read so you can read it again without a connection. If a fetch fails, the app shows a failure notice with a link to the website.

**Languages:** this document is published in several languages. The English version is the original; the translations are provided for convenience. If a translation conflicts with the English version, the English version prevails.

---

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa
