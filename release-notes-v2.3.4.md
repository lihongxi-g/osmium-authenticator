# Osmium v2.3.4

Released 2026-08. Automatic-backup reliability fixes and update-check behavior change.

## Fixed

- **Cold-start scheduling bug**: the schedule-maintenance check ran at app start with the settings flow still carrying its defaults, so it silently skipped — after a force-stop, the schedule was not restored as designed. The check now observes the settings flow and runs once the real values load.
- **Update check runs on every app open** (previously limited to once per day, which contradicted the feature description). A short internal cooldown (60 s) remains to respect GitHub's unauthenticated rate limits.

## Improved

- **Missed-backup catch-up**: when the app opens and the last successful automatic backup is older than the configured interval, a backup runs immediately. On devices whose battery policies freeze scheduled jobs overnight, opening the app now guarantees a backup instead of waiting for the system to process the overdue job.
- **"Back up now" requests run as expedited work** (Android 12+), giving user-triggered backups higher execution priority. Scheduled jobs keep their delayed timing — WorkManager does not allow expedited work with an initial delay.

## Docs

- All "once per day" update-check wording removed from the README, privacy policy, terms (EN/ZH) and the in-app texts (9 languages).
- ColorOS guidance: to guarantee night-time backups, allow Osmium to run fully in the background (Settings → Battery → More settings) and allow it to auto-start (Phone Manager → App management → Auto-start management). With restrictions in place the backup still runs on app open thanks to the catch-up above.

The signing certificate fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
