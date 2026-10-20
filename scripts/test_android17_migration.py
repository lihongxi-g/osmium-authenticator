"""Configuration and permission-entry regression checks for the API 37 migration."""
from pathlib import Path

import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/safekey/authenticator"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class Android17MigrationTest(unittest.TestCase):
    def test_target_and_compile_sdk_are_37(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        for key in ("compileSdk", "targetSdk"):
            self.assertRegex(gradle, rf"{key}\s*=\s*37\b")
        self.assertRegex(gradle, r"minSdk\s*=\s*26\b")

    def test_network_policy_is_declared_in_manifest(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = manifest.find("application")
        assert app is not None
        self.assertEqual(app.get(ANDROID + "networkSecurityConfig"), "@xml/network_security_config")
        self.assertIsNone(app.get(ANDROID + "usesCleartextTraffic"))
        config = ET.parse(ROOT / "app/src/main/res/xml/network_security_config.xml").getroot()
        base = config.find("base-config")
        assert base is not None
        self.assertEqual(base.get("cleartextTrafficPermitted"), "true")
        self.assertFalse(config.findall(".//certificateTransparency[@enabled='false']"))
        self.assertFalse(config.findall(".//certificates[@src='user']"))

    def test_lan_screen_does_not_start_children_before_permission(self):
        screen = (SRC / "ui/screens/LanTransferScreen.kt").read_text()
        self.assertIn("LocalNetworkPermissionGate", screen)
        self.assertNotIn("private const val ANDROID_LOCAL_NETWORK_PERMISSION", screen)

    def test_webdav_and_worker_check_permission(self):
        # The unattended backup pipeline (worker + inline "back up now") lives
        # in AutoBackupRunner; the LAN gate must be checked there before any
        # upload leaves the device.
        for path in (
            "ui/screens/WebDavScreen.kt",
            "backup/AutoBackupRunner.kt",
            "backup/AutoBackupWorker.kt",
        ):
            if path.endswith("AutoBackupWorker.kt"):
                # The worker delegates to the runner instead of duplicating it.
                self.assertIn(
                    "AutoBackupRunner.runAndRecord", (SRC / path).read_text(), path
                )
                continue
            self.assertIn("LocalNetworkAccess.checkWebDav", (SRC / path).read_text(), path)

    def test_ci_installs_api37(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        self.assertIn('"platforms;android-37.0"', workflow)
        self.assertNotIn('"platforms;android-35"', workflow)

    def test_translations_have_identical_keys(self):
        files = sorted((ROOT / "app/src/main/res").glob("values*/strings.xml"))
        expected = None
        for path in files:
            keys = [node.get("name") for node in ET.parse(path).getroot()]
            self.assertEqual(len(keys), len(set(keys)), str(path))
            if expected is None:
                expected = set(keys)
            self.assertEqual(expected, set(keys), str(path))


if __name__ == "__main__":
    unittest.main()
