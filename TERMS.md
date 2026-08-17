# Osmium Terms of Use

*Effective date: August 16, 2026*

By installing or using Osmium, you agree to the following terms.

## License

Osmium is free and open source software released under the MIT License. You may use, copy, modify and redistribute it in accordance with that license, which is included in this repository (LICENSE).

## Provided "as is"

The software is provided without warranty of any kind, express or implied. To the maximum extent permitted by law, the author is not liable for any damages arising from the use of or inability to use the software.

## Your responsibilities

- **You are responsible for safeguarding your secrets and backup passwords.** Osmium encrypts data with keys that cannot be recovered by the developer or anyone else. A forgotten PIN or lost backup password cannot be reset. The automatic-backup password is stored on your device (encrypted with the Android Keystore) and is likewise unrecoverable if the app's data is erased.
- **You are responsible for keeping backups.** Uninstalling the application or clearing its data permanently deletes all accounts; the developer cannot recover them. Automatic backups run unattended via the Android system scheduler and depend on the device's power state and background-activity settings — see the README for a recommendation; the app does not keep any persistent background service and does not drain the battery beyond the short backup job.
- **The self-destruct PIN is irreversible.** Entering it at a PIN prompt destroys all data permanently, by design.
- **You are responsible for keeping your device clock accurate.** Time-based codes depend on the device clock; the application offers a calibration option but cannot fix an incorrect clock on its own.

## Permitted use

You may use Osmium for your own two-factor authentication needs. You may not use the software to access systems or data you are not authorized to access.

## Third-party services

Osmium runs no account system and connects to no third-party services except:

- the WebDAV server **you configure yourself** (optional backup feature; backups are encrypted with your password before upload), and
- the GitHub releases API (`api.github.com`) for the optional update check (run when the app opens), which sends no account or device data.

The optional Google Authenticator migration feature parses export codes you provide locally on the device; no data is sent to Google or anyone else.

## Contact

Email: zhif0776@hotmail.com · Telegram: https://t.me/osmium2fa
