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

CI artifact SHA-256 values: arm64-v8a `ae0d1d418e9a4f076c502a1fd39c0c81bf36e0e377441cba6c5b3275f3abf203`; armeabi-v7a `2871472d50355037da27d048d831801ce9a6f34fd033605ca1dd50af38bae55d`; x86_64 `c3f39ae3e26b1807846cf1daef2d880d749b8805520db1e527ff6b4fdeb33c6d`. Re-read and compare GitHub asset digests after upload.
