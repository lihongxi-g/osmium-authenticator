# Release 2.3.9: documentation and website synchronization

## Summary

This release updates Osmium to version 2.3.9 (versionCode 46) and includes the tag settings/colour changes and scroll-state fixes from the application commit. It also retains the LAN quick-transfer implementation present in the release branch.

## Included

- Home and Settings scroll position preservation when returning from child screens.
- Optional Tags feature switch with a second-level settings page.
- Hidden tag surfaces when Tags is disabled; no `Uncategorized` filter when no user tags exist.
- Muted palette and validated custom `#RRGGBB` tag colours, with legacy named-colour compatibility.
- LAN quick transfer between two devices on the same Wi-Fi using the existing 6-digit pairing flow, encrypted transfer, receiver preview and selective import.
- Updated README, WebDAV guide, Terms of Use, Privacy Policy and all in-app legal/network copy.
- Release APK naming in the form `osmium-2.3.9-<architecture>.apk`.

## Public links

- GitHub Release: https://github.com/lihongxi-g/osmium-authenticator/releases/tag/v2.3.9
- Mainland download node: https://download.osmium.im/
- Main website: https://osmium.im/

The final SHA-256 values come from the same CI artifact's `SHA256SUMS.txt`; re-read and compare GitHub asset digests after upload.
