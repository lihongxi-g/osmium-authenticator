# Osmium v2.3.5

Released 2026-08. Automatic-backup target logic fix.

## Fixed

- **No more forced WebDAV setup.** Previously, the default backup target was WebDAV, so users who had never configured a WebDAV server were blocked from enabling automatic backup — the app demanded a WebDAV server that they had not set up. Now:
  - The **default target is local** (the phone's Download/Osmium folder), so automatic backup works out of the box.
  - In the target picker, the WebDAV option is **greyed out when no WebDAV server is configured**, with a note explaining that it is unavailable until configured.
  - As a safety net, if the stored target is WebDAV but the configuration has since been removed, enabling a backup or tapping "Back up now" automatically falls back to local backup (with a toast) instead of blocking.

## Docs

- No documentation changes were required for this fix.

The signing certificate fingerprint is unchanged: `B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412`.
