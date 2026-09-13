# keyattestation (vendored)

Source-level copy of [android/keyattestation](https://github.com/android/keyattestation),
Google's official Android Key Attestation certificate-chain verifier
(Apache-2.0). Upstream does not publish this library to Maven; embedding the
sources is the intended integration model.

- **Pinned commit**: `100dedaea2387d9705d29b2be3177466c33ae17b` (2026-09-11)
- **License**: Apache-2.0 — see `LICENSE` (kept from upstream). The exact list
  of modifications is in `NOTICE`.
- **Consumed by**: the `:app` module (K3 hardware-proof layer of the device
  integrity feature).
- **Tests**: `./gradlew :keyattestation:test` runs the upstream test suite
  (JUnit Platform + vintage engine) against the vendored sources and the
  checked-in `testdata/` device chains.

## Layout vs. upstream

- `src/main/kotlin/` — verifier sources (same as upstream), plus the
  hand-written `GoogleTrustAnchors.kt` runtime loader (see NOTICE item 3).
- `src/test/kotlin/` — upstream test suite; the `testing/` helpers were moved
  here from `src/main/kotlin/testing/` (NOTICE item 2).
- `testdata/` — 50 real-device attestation chains and negative samples used by
  the test suite (same as upstream).
- `roots.json` — Google root certificates. Single copy: read from disk by the
  test suite, published to the classpath by `processResources`, and loaded at
  runtime by `GoogleTrustAnchors`. Refresh from
  `https://android.googleapis.com/attestation/root`.
- `NOTICE` — compliance statement and exact list of modifications.

## Updating from upstream

1. Download the new revision (tarball at the new commit).
2. Diff it against the previous pin and re-apply the modifications listed in
   NOTICE (file removals, `testing/` move, build-script adaptation).
3. Update the pin in this README and in NOTICE.
4. Run `./gradlew :keyattestation:test` in CI.
