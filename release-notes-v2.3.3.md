# Osmium v2.3.3

Released 2026-08. Fixes and one new setting.

## Fixed

- The plaintext-connection warning for `http://` WebDAV addresses: tapping "Save anyway" now actually saves the configuration. Previously the confirmation re-triggered the warning check, so the dialog never went away.
- Auto-backup pruning order is now deterministic: backups are ordered by their timestamped file name. On some WebDAV servers the `lastmodified` property is missing (0), which could previously scramble the order and delete the wrong files.

## New

- **Backups to keep** — a setting on the auto-backup screen: how many auto-backup files are kept per target (WebDAV server or Download/Osmium folder). Older files beyond the count are deleted after each backup. Range 1–10, default 5.

## Docs

- In-app privacy policy and manual (all 9 languages) and the GitHub privacy policy now state the configurable retention instead of a fixed 5.

The signing certificate fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
