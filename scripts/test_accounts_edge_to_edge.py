"""Source guards for inset ownership on scrolling screens (not UI tests)."""
import re
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

    def test_home_filter_is_part_of_the_account_list(self):
        self.assertIn('item(key = 0) { filterRow() }', SCREEN)
        self.assertIn("if (!showAccountList) filterRow?.invoke()", SCREEN)

    def test_compact_toolbar_preserves_search_inset(self):
        self.assertIn("expandedHeight = 48.dp", TOP_BAR)
        self.assertIn("Modifier.size(48.dp)", TOP_BAR)
        self.assertIn(".statusBarsPadding()", SCREEN)
        self.assertIn(".padding(horizontal = 16.dp)", CHIPS)

    def test_scaffold_bottom_padding_scrolls_with_page_content(self):
        for name in ("SettingsScreen", "AppearanceScreens", "AccountFormScreen", "AboutScreen", "TransferScreens"):
            with self.subTest(name=name):
                screen = (SRC / f"ui/screens/{name}.kt").read_text(encoding="utf-8")
                self.assertIn(".scrollBelowTopBar(padding", screen)
                self.assertNotRegex(screen, r"\.padding\(padding\)\s*\.verticalScroll")
        scroll_modifier = TOP_BAR.split("fun Modifier.scrollBelowTopBar", 1)[-1]
        self.assertRegex(
            scroll_modifier,
            re.compile(r"\.padding\(fixedPadding\).*?\.verticalScroll\(state\)"
                       r"\s*\.padding\(scrollPadding\)", re.S),
        )
        self.assertIn("override fun calculateBottomPadding() = padding.calculateBottomPadding()", scroll_modifier)

    def test_settings_first_section_does_not_inherit_the_inter_section_gap(self):
        settings = (SRC / "ui/screens/SettingsScreen.kt").read_text(encoding="utf-8")
        self.assertIn('SectionHeader(stringResource(R.string.settings_appearance), topSpacing = 4.dp)', settings)


if __name__ == "__main__":
    unittest.main()