"""Source guards for inset ownership on the accounts screen (not UI tests)."""
from pathlib import Path
import unittest


SRC = Path(__file__).resolve().parents[1] / "app/src/main/java/com/safekey/authenticator"
SCREEN = (SRC / "ui/screens/AccountsScreen.kt").read_text(encoding="utf-8")
CHIPS = (SRC / "ui/components/FilterChipRow.kt").read_text(encoding="utf-8")
TOP_BAR = (SRC / "ui/components/Common.kt").read_text(encoding="utf-8")


class AccountsEdgeToEdgeTest(unittest.TestCase):
    maxDiff = 0

    def test_system_bottom_inset_is_inside_the_list_content(self):
        self.assertNotIn(".padding(padding)", SCREEN)
        self.assertRegex(SCREEN, r"bottom\s*=\s*bottomInset\s*\+\s*AccountListFabClearance")
        self.assertRegex(
            SCREEN,
            r"LazyColumn\(\s*modifier = Modifier.fillMaxSize\(\),\s*"
            r"state = state,\s*contentPadding = contentPadding",
        )

    def test_non_list_states_keep_the_bottom_safe_area(self):
        self.assertIn("if (showAccountList) 0.dp else bottomInset", SCREEN)

    def test_home_header_compacts_without_padding_the_chips(self):
        self.assertIn("expandedHeight = 56.dp", SCREEN)
        self.assertIn("expandedHeight = expandedHeight", TOP_BAR)
        self.assertIn(".statusBarsPadding()", SCREEN)
        self.assertIn(".padding(horizontal = 16.dp)", CHIPS)


if __name__ == "__main__":
    unittest.main()