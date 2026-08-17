# Osmium v2.3.4

Released 2026-08. Automatic-backup reliability fixes and update-check behavior change.

## Fixed

- **Cold-start scheduling bug**: the schedule-maintenance check ran at app start with the settings flow still carrying its defaults, so it silently skipped — after a force-stop, the schedule was not restored as designed. The check now observes the settings flow and runs once the real values load.
- **Update check runs on every app open** (previously limited to once per day, which contradicted the feature description). A short internal cooldown (60 s) remains to respect GitHub's unauthenticated rate limits.

## Improved

- **One-tap background-activity grant**: the Auto backup screen now shows the live background-activity status and, when not granted, offers a button that opens the system's battery-optimization dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Auto-start is different: it is a vendor-specific feature with **no standard Android API**, so the app cannot request it — the warning on the Auto backup screen now emphasizes that it must be enabled manually. See the README for manufacturer examples.

- **Missed-backup catch-up**: when the app opens and the last successful automatic backup is older than the configured interval, a backup runs immediately. On devices whose battery policies freeze scheduled jobs overnight, opening the app now guarantees a backup instead of waiting for the system to process the overdue job.
- **"Back up now" requests run as expedited work on Android 12+**, giving user-triggered backups higher execution priority. On Android 8–11 expedited work would require a foreground-service notification (and would fail without extra worker plumbing), so those versions run a plain immediate request instead. Scheduled jobs keep their delayed timing — WorkManager does not allow expedited work with an initial delay.
- **Android 17 readiness**: the `ACCESS_LOCAL_NETWORK` permission is declared in advance. Android 17 requires it at runtime for local-network traffic once an app targets API 37 — WebDAV backups to a LAN NAS depend on it. A dedicated error message now appears if the system blocks local-network access (Android 16+ protection returns EPERM/ECONNABORTED).

## Docs

- All "once per day" update-check wording removed from the README, privacy policy, terms (EN/ZH) and the in-app texts (9 languages).
- **README and Terms (EN/ZH) now strongly recommend manually enabling both "allow auto-start" and "full background activity" for Osmium**, and state that the names and paths of these switches differ between manufacturers and Android versions — users should locate them in their own device's settings (ColorOS examples given). Without auto-start, unattended night-time backups will not run on aggressive ROMs; opening the app still triggers the catch-up backup.

The signing certificate fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
