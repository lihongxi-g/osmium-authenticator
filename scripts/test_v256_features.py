"""Regression checks for 2.5.6: digits, local-time backup, settings sub-pages."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"


def sources():
    return {p: p.read_text() for p in MAIN.rglob("*") if p.suffix in (".kt", ".xml")}


class Release256FeaturesTest(unittest.TestCase):
    def test_digits_supported_without_a_developer_flag(self):
        account = (MAIN / "java/com/safekey/authenticator/model/Account.kt").read_text()
        digits = re.search(r"SUPPORTED_DIGITS\s*=\s*listOf\(([^)]*)\)", account)
        assert digits is not None
        self.assertEqual([4, 5, 6, 7, 8], [int(d) for d in re.findall(r"\d+", digits.group(1))])

    def test_no_developer_digits_toggle_remains(self):
        for path, text in sources().items():
            self.assertNotRegex(text, r"devExtraDigits|DEV_EXTRA_DIGITS|dev_extra_digits|allowExtraDigits|digitsSupported", str(path))

    def test_digits_are_not_gated_in_the_entry_points(self):
        parser = (MAIN / "java/com/safekey/authenticator/totp/OtpUriParser.kt").read_text()
        self.assertIn("Account.SUPPORTED_DIGITS", parser)
        importer = (MAIN / "java/com/safekey/authenticator/totp/importer/ImportSupport.kt").read_text()
        self.assertIn("Account.SUPPORTED_DIGITS", importer)

    def test_backup_times_follow_the_device_timezone(self):
        for path, text in sources().items():
            self.assertNotIn("GMT+8", text, str(path))
        screen = (MAIN / "java/com/safekey/authenticator/ui/screens/AutoBackupScreen.kt").read_text()
        self.assertIn("AutoBackupScheduler.zoneLabel()", screen)
        self.assertIn("auto_backup_tz_label", screen)
        scheduler = (MAIN / "java/com/safekey/authenticator/backup/AutoBackupScheduler.kt").read_text()
        self.assertIn("fun zoneLabel(", scheduler)
        self.assertIn("ZoneId.systemDefault()", scheduler)

    def test_theme_and_language_are_secondary_pages(self):
        navigation = (MAIN / "java/com/safekey/authenticator/ui/navigation/Navigation.kt").read_text()
        self.assertIn("object ThemeSettings", navigation)
        self.assertIn("object LanguageSettings", navigation)
        settings = (MAIN / "java/com/safekey/authenticator/ui/screens/SettingsScreen.kt").read_text()
        self.assertIn("Screen.ThemeSettings", settings)
        self.assertIn("Screen.LanguageSettings", settings)
        self.assertNotIn("showThemeDialog", settings)
        self.assertNotIn("showLanguageDialog", settings)
        activity = (MAIN / "java/com/safekey/authenticator/MainActivity.kt").read_text()
        self.assertIn("ThemeSettingsScreen(", activity)
        self.assertIn("LanguageSettingsScreen(", activity)


    def test_no_unescaped_apostrophes_in_resources(self):
        """aapt2 rejects `'` inside a resource value unless it is escaped."""
        pattern = re.compile(r"<(?:string|item)[^>]*>(.*?)</(?:string|item)>", re.S)
        for path in sorted((ROOT / "app/src/main/res").glob("values*/strings.xml")):
            for match in pattern.finditer(path.read_text()):
                self.assertIsNone(
                    re.search(r"(?<!\\)'", match.group(1)),
                    f"{path.parent.name}: {match.group(1)[:60]}",
                )

    def test_version_and_ci_guard_agree(self):
        """build.gradle.kts and the CI release tripwire must name one version.

        The tripwire lives in the workflow; deriving it here means a version
        bump touches two files instead of three, and a half-done bump still
        fails loudly.
        """
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
        assert version is not None
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        pinned = re.search(r'test\s+"\$version"\s*=\s*"([^"]+)"', workflow)
        assert pinned is not None, "the CI version tripwire is missing"
        self.assertEqual(pinned.group(1), version.group(1))


if __name__ == "__main__":
    unittest.main()
