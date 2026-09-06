# Privacy Policy

*Effective date: September 6, 2026 · Osmium v2.4.0*

*This document mirrors the in-app Privacy Policy (About screen).*

Osmium does not collect, sell or use personal data for advertising or analytics. There is no account system, no registration, no advertising SDK and no crash reporter, and no Osmium cloud account or synchronization server.

Storage: authenticator secrets, account names, issuers, tags and tag colors are stored on this device only, encrypted with AES-256-GCM using a non-exportable Android Keystore key. Exported backups are encrypted with your password (PBKDF2-HMAC-SHA256 with 120,000 iterations, then AES-256-GCM). The WebDAV login and the automatic-backup password are stored locally, encrypted with the Keystore key.

Import: importing from Google Authenticator QR codes and from Aegis, 2FAS and Raivo OTP export files happens entirely on this device. The selected file is read into memory to build the import preview; it is not copied, uploaded or sent anywhere. Only the entries you confirm are saved into the encrypted vault; everything else is discarded.

Network: the app connects only when the relevant feature is enabled or initiated. WebDAV connects to the address you configure; GitHub update checks request public version information without account or device data; LAN quick transfer connects two devices on the same Wi-Fi for one temporary encrypted session.

Automatic backups use Android system scheduling and may be deferred by manufacturer power policies. They write encrypted files to your configured WebDAV server or to the Download/Osmium folder (newest 5 kept by default, up to 10 configurable; older files are pruned). Camera scanning, QR reading and biometrics are handled on-device by Android. Wi-Fi state and multicast are used for LAN peer discovery only while the transfer screen is open. WRITE_EXTERNAL_STORAGE applies only to automatic backups on Android 8/9. ACCESS_LOCAL_NETWORK is declared for future platform requirements and is not requested on targetSdk 34.

We do not share data with advertisers, analytics services or an Osmium cloud. A WebDAV server receives encrypted files you upload; the intended LAN receiver receives data from a transfer you start; GitHub receives only a public version query. Uninstalling or clearing app data does not delete copies you saved elsewhere.

---

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa
