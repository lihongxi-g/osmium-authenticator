# Privacy Policy

*Effective date: September 23, 2026 · Applies to Osmium v2.5.3 and later*

*Current version, published at osmium.im and fetched by the app on launch (About → Privacy Policy).*

Osmium does not collect, sell or use personal data for advertising or analytics. There is no account system, no registration, no advertising SDK and no crash reporter, and no Osmium cloud account or synchronization server.

Storage: authenticator secrets, account names, issuers, tags and tag colors are stored on this device only, encrypted with AES-256-GCM using a non-exportable Android Keystore key. Exported backups are encrypted with your password (PBKDF2-HMAC-SHA256 with 120,000 iterations, then AES-256-GCM). The WebDAV login and the automatic-backup password are stored locally, encrypted with the Keystore key. If you enable developer mode's plaintext export, the exported file is not encrypted: it contains all secrets in readable form and is protected only by where you keep it.

Device integrity: at launch Osmium checks the device locally — an early boot-time snapshot, system, mount, kernel, boot-state and app-signature checks, cross-checks between them, and hardware-backed key attestation (TEE certificate chain, verified-boot chain and boot hash) verified on this device with Google's official verifier against Google root certificates bundled inside the app. Root-manager detection asks Android about a short, fixed list of well-known root-manager package names; the app does not enumerate the apps installed on your device. All of these checks run entirely on this device, make no network requests, and their results are never stored or transmitted — they appear on the integrity screen, and a suspicious or compromised result shows a reminder once per app open. An installed root-manager app is treated as a hint, not as proof. Detailed detection logging is off by default and writes local log lines only when you enable it in developer mode. Developer mode can hide this feature; once hidden, the app no longer runs these checks at launch and no longer shows the reminder.

Attestation revocation data: the app ships a snapshot of Google's attestation revocation status list, taken when the app was built. If you tap refresh on the integrity screen, it downloads a fresh copy from https://android.googleapis.com/attestation/status and stores it in the app's private storage, replacing the previous copy only after the download parses successfully. The request carries no account, authenticator or device data; as with any HTTP request the server sees the usual connection metadata such as your IP address. If the refresh fails or your network blocks it, the previous data stays in use and attestation verification keeps working offline.

Import: importing from Google Authenticator QR codes and from Aegis, 2FAS and Raivo OTP export files happens entirely on this device. The selected file is read into memory to build the import preview; it is not copied, uploaded or sent anywhere. Only the entries you confirm are saved into the encrypted vault; everything else is discarded.

Network: the app opens a connection only in these cases: (1) each time it opens, it fetches the legal documents from osmium.im (below); (2) version checks against the GitHub Releases API — enabled by default and switchable in Settings — which run in the background when the app comes to the foreground; (3) WebDAV, when you enable automatic backups or start a backup, connecting to the address you configure; (4) LAN quick transfer, which connects to the other device while the transfer screen is open; (5) the revocation-data refresh described above, only when you tap it; and (6) opening a link, which hands the address to your browser or app store. None of these requests carry your accounts, your secrets or device identifiers.

Legal documents: each time the app opens, it fetches the current versions of the Terms of Use and of the Privacy Policy from osmium.im so that you always see the versions in effect. These requests carry no account, authenticator or device data. If the fetch fails, the app shows a failure notice with a link to the website.

Version checks: when the version check is enabled, the app requests https://api.github.com/repos/lihongxi-g/osmium-authenticator/releases/latest on launch to see whether a newer release exists. The request sends only a User-Agent containing the app name and version; no account, authenticator or device data is sent, and failures stay silent. You can switch the check off in Settings.

Automatic backups use Android system scheduling and may be deferred by manufacturer power policies. They write encrypted files to your configured WebDAV server or to the Download/Osmium folder (newest 5 kept by default, up to 10 configurable; older files are pruned). Camera scanning, QR reading and biometrics are handled on-device by Android. Wi-Fi state and multicast are used for LAN peer discovery only while the transfer screen is open. WRITE_EXTERNAL_STORAGE applies only to automatic backups on Android 8/9. ACCESS_LOCAL_NETWORK is declared for future platform requirements and is not requested on targetSdk 34.

We do not share data with advertisers, analytics services or an Osmium cloud. A WebDAV server receives encrypted files you upload; the intended LAN receiver receives data from a transfer you start; GitHub receives only a public version query; Google's attestation status endpoint receives only the request for its public revocation list when you refresh it manually; osmium.im serves the public legal documents the app reads, and no user data is sent to it. Uninstalling or clearing app data does not delete copies you saved elsewhere.

---

Contact: zhif0776@hotmail.com · https://t.me/osmium2fa
