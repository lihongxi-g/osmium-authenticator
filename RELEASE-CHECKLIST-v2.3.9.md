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

CI artifact SHA-256 values: arm64-v8a `910e4f24ab18e246cd7d71444d86b91ddc820ffd91c280edc6c7dcc575dfac35`; armeabi-v7a `e6694a229f09abbdc2b9fdea81228eb3ef91dd0e9548647b1af33cf99cbbf8c4`; x86_64 `a67970e2ed1af9b808ca61f1e42be53a8c2b17a12d3c611083bb89d80aa5e249`. Re-read and compare GitHub asset digests after upload.
