"""Guard the entry points where long localized labels compete for card width."""

from pathlib import Path
import re
import unittest


UI = Path(__file__).resolve().parents[1] / "app/src/main/java/com/safekey/authenticator/ui"


class LocalizedCardLayoutTest(unittest.TestCase):
    def test_permission_cards_use_the_shared_adaptive_setting_row(self):
        settings_row = (UI / "components/Common.kt").read_text().split("fun SettingRow(", 1)[1]
        settings_row = settings_row.split("\n}\n", 1)[0]
        self.assertIn("AdaptiveTrailingRow(", settings_row)
        self.assertIn("trailing = trailing", settings_row)
        permissions = (UI / "screens/PermissionsScreen.kt").read_text()
        self.assertIn("SettingRow(", permissions)
        self.assertIn("text = statusText", permissions)

    def test_custom_rows_with_variable_width_statuses_or_actions_also_adapt(self):
        cases = {
            "IntegrityScreen": "text = integrityCheckTitle(check.id)",
            "AutoBackupScreen": "R.string.auto_backup_bg_granted",
            "LanTransferScreen": "R.string.lan_server_ip_label",
        }
        for screen, marker in cases.items():
            with self.subTest(screen=screen):
                source = (UI / f"screens/{screen}.kt").read_text()
                row_prefix = source[:source.index(marker)]
                rows = re.findall(r"\b(AdaptiveTrailingRow|Row)\(", row_prefix)
                self.assertEqual(rows[-1], "AdaptiveTrailingRow")


if __name__ == "__main__":
    unittest.main()