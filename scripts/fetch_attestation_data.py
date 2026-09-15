#!/usr/bin/env python3
"""Refresh the attestation data snapshots used by the Android build.

Run MANUALLY to refresh the committed snapshots (this is deliberately NOT
part of the release build: the APK must be reproducible from the tagged
source alone, so the build only consumes the committed files). A network
failure (Google unreachable) or a suspicious payload must never fail
anything — the previous snapshots stay in place. The app runtime is
offline-first and degrades gracefully, so stale data is acceptable; broken data is not, hence
the shape validation before anything is written.

Usage: python3 scripts/fetch_attestation_data.py
"""

import json
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROOTS_PATH = ROOT / "keyattestation" / "roots.json"
STATUS_PATH = ROOT / "app" / "src" / "main" / "assets" / "attestation_status.json"

ROOTS_URL = "https://android.googleapis.com/attestation/root"
STATUS_URL = "https://android.googleapis.com/attestation/status"


def fetch(url: str, timeout: int = 20) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "osmium-build"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        if getattr(response, "status", 200) != 200:
            raise RuntimeError(f"HTTP {response.status} for {url}")
        return response.read()


def refresh_roots() -> None:
    try:
        payload = fetch(ROOTS_URL)
        roots = json.loads(payload)
        # The live endpoint ships a small set (currently 2 roots: RSA + ECDSA).
        if not isinstance(roots, list) or not roots or len(roots) > 100:
            raise ValueError(f"unexpected roots payload shape (count={len(roots) if isinstance(roots, list) else 'n/a'})")
        if not all(isinstance(c, str) and "BEGIN CERTIFICATE" in c for c in roots):
            raise ValueError("roots payload contains non-certificate entries")
        ROOTS_PATH.write_bytes(payload)
        print(f"roots.json refreshed: {len(roots)} certificates")
    except Exception as exc:  # noqa: BLE001 - best effort
        print(f"roots.json refresh skipped: {exc}")


def refresh_status() -> None:
    try:
        payload = fetch(STATUS_URL)
        status = json.loads(payload)
        entries = status.get("entries")
        if not isinstance(entries, dict) or len(entries) < 100:
            raise ValueError(
                "unexpected status payload shape "
                f"(entries={len(entries) if isinstance(entries, dict) else 'n/a'})"
            )
        STATUS_PATH.parent.mkdir(parents=True, exist_ok=True)
        STATUS_PATH.write_bytes(payload)
        print(f"attestation_status.json refreshed: {len(entries)} entries")
    except Exception as exc:  # noqa: BLE001 - best effort
        print(f"attestation_status.json refresh skipped: {exc}")


def main() -> int:
    refresh_roots()
    refresh_status()
    return 0


if __name__ == "__main__":
    sys.exit(main())
